package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Thin Hyperswitch Payments API client (no official SDK).
 * Active when {@code platform.payment.hyperswitch.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "platform.payment.hyperswitch", name = "enabled", havingValue = "true")
public class HyperswitchHttpPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(HyperswitchHttpPaymentProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HyperswitchPaymentProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public HyperswitchHttpPaymentProvider(HyperswitchPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderCode code() {
        return new ProviderCode("hyperswitch");
    }

    @Override
    public CheckoutResult createCheckout(InitiateCheckoutCommand command) {
        long amount = command.amount().amountMinor();
        String currency = command.amount().currency();
        String success = command.successUrl() != null ? command.successUrl() : properties.getSuccessUrl();
        String cancel = command.cancelUrl() != null ? command.cancelUrl() : properties.getCancelUrl();

        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("amount", Math.max(amount, 1L));
            body.put("currency", currency);
            body.put("payment_link", true);
            if (properties.getProfileId() != null && !properties.getProfileId().isBlank()) {
                body.put("profile_id", properties.getProfileId());
            }
            ObjectNode metadata = body.putObject("metadata");
            metadata.put("checkout_session_id", command.checkoutSessionId());
            metadata.put("tenant_id", command.principal().tenantId());
            metadata.put("user_id", command.principal().principalId());
            metadata.put("product_reference", command.productReference());
            ObjectNode paymentLinkConfig = body.putObject("payment_link_config");
            paymentLinkConfig.put("redirect_url", success);
            paymentLinkConfig.put("cancel_url", cancel);

            String endpoint = trimTrailingSlash(properties.getBaseUrl()) + "/payments";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("api-key", properties.getApiKey())
                    .header("x-idempotency-key", command.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Hyperswitch HTTP " + response.statusCode() + ": " + response.body());
            }
            String respBody = response.body();
            String paymentId = extractJsonField(respBody, "payment_id");
            if (paymentId == null) {
                paymentId = extractJsonField(respBody, "id");
            }
            String linkUrl = extractNestedField(respBody, "payment_link", "payment_link_url");
            if (linkUrl == null) {
                linkUrl = extractJsonField(respBody, "payment_link_url");
            }
            String reference = paymentId != null ? paymentId : "hs-unknown";
            log.info("Hyperswitch payment created ref={} for {}", reference, command.checkoutSessionId());
            return new CheckoutResult(reference, linkUrl != null ? linkUrl : success);
        } catch (Exception e) {
            log.error("Hyperswitch createCheckout failed for {}: {}", command.checkoutSessionId(), e.getMessage());
            throw new IllegalStateException("Hyperswitch payment creation failed", e);
        }
    }

    @Override
    public PaymentVerificationResult verifyPayment(ProviderVerificationRequest command) {
        String ref = command.providerReference();
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("providerReference is required");
        }
        try {
            String endpoint = trimTrailingSlash(properties.getBaseUrl()) + "/payments/" + ref;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("api-key", properties.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return new PaymentVerificationResult(false, "http_" + response.statusCode(), PaymentState.PENDING);
            }
            String status = extractJsonField(response.body(), "status");
            boolean paid = "succeeded".equalsIgnoreCase(status) || "charged".equalsIgnoreCase(status);
            return new PaymentVerificationResult(paid, status != null ? status : "unknown",
                    paid ? PaymentState.SETTLED : PaymentState.PENDING);
        } catch (Exception e) {
            log.warn("Hyperswitch verifyPayment failed for {}: {}", ref, e.getMessage());
            return new PaymentVerificationResult(false, "error", PaymentState.PENDING);
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest command) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("payment_id", command.providerReference());
            body.put("refund_id", command.idempotencyKey());
            body.put("amount", command.amount().amountMinor());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/refunds"))
                    .header("Content-Type", "application/json")
                    .header("api-key", properties.getApiKey())
                    .header("x-idempotency-key", command.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return new ProviderRefundResult(false, null, "http_" + response.statusCode());
            String reference = extractJsonField(response.body(), "refund_id");
            if (reference == null) reference = extractJsonField(response.body(), "id");
            return new ProviderRefundResult(true, reference, extractJsonField(response.body(), "status"));
        } catch (Exception failure) {
            throw new IllegalStateException("Hyperswitch refund failed", failure);
        }
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        return WebhookPayloadSupport.parseCommerceWebhook(body);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://sandbox.hyperswitch.io";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) {
            return null;
        }
        String pattern = "\"" + field + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            pattern = "\"" + field + "\": \"";
            idx = json.indexOf(pattern);
        }
        if (idx < 0) {
            return null;
        }
        int start = idx + pattern.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static String extractNestedField(String json, String objectField, String innerField) {
        if (json == null) {
            return null;
        }
        int objIdx = json.indexOf("\"" + objectField + "\"");
        if (objIdx < 0) {
            return null;
        }
        int sliceStart = objIdx;
        int sliceEnd = Math.min(json.length(), sliceStart + 2000);
        return extractJsonField(json.substring(sliceStart, sliceEnd), innerField);
    }

    private static String extractMetadata(String body, String key) {
        if (body == null) {
            return null;
        }
        for (String pattern : new String[] {"\"" + key + "\":\"", "\"" + key + "\": \""}) {
            int idx = body.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                int end = body.indexOf('"', start);
                if (end > start) {
                    return body.substring(start, end);
                }
            }
        }
        return null;
    }
}
