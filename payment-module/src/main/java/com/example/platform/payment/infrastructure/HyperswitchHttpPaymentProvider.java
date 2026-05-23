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
    public CheckoutResult createCheckout(CheckoutCommand command) {
        long amount = command.amountMinor() != null ? command.amountMinor() : 0L;
        String currency = command.currencyCode() != null ? command.currencyCode().toUpperCase() : "USD";
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
            if (command.tenantId() != null) {
                metadata.put("tenant_id", command.tenantId());
            }
            if (command.userId() != null) {
                metadata.put("user_id", command.userId());
            }
            metadata.put("canonical_product_code", command.canonicalProductCode());
            ObjectNode paymentLinkConfig = body.putObject("payment_link_config");
            paymentLinkConfig.put("redirect_url", success);
            paymentLinkConfig.put("cancel_url", cancel);

            String endpoint = trimTrailingSlash(properties.getBaseUrl()) + "/payments";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("api-key", properties.getApiKey())
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
    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        String ref = command.providerReference();
        if (ref == null || ref.isBlank()) {
            return new PaymentVerificationResult(false, "missing_reference", "unknown");
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
                return new PaymentVerificationResult(false, "http_" + response.statusCode(), "unknown");
            }
            String status = extractJsonField(response.body(), "status");
            boolean paid = "succeeded".equalsIgnoreCase(status) || "charged".equalsIgnoreCase(status);
            return new PaymentVerificationResult(paid, status != null ? status : "unknown", paid ? "paid" : "pending");
        } catch (Exception e) {
            log.warn("Hyperswitch verifyPayment failed for {}: {}", ref, e.getMessage());
            return new PaymentVerificationResult(false, "error", "unknown");
        }
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        WebhookParseResult parsed = WebhookPayloadSupport.parseCommerceWebhook(body, "hyperswitch-webhook");
        if (parsed.checkoutSessionId() != null) {
            return parsed;
        }
        String sessionId = extractMetadata(body, "checkout_session_id");
        String tenantId = extractMetadata(body, "tenant_id");
        String userId = extractMetadata(body, "user_id");
        String ref = extractJsonField(body, "payment_id");
        if (ref == null) {
            ref = extractJsonField(body, "id");
        }
        String status = extractJsonField(body, "status");
        boolean paid = status != null && ("succeeded".equalsIgnoreCase(status) || "charged".equalsIgnoreCase(status));
        if (body != null && (paid || body.contains("payment_succeeded") || body.contains("payment_success"))) {
            return new WebhookParseResult(
                    "payment.succeeded", 1,
                    ref != null ? ref : "hyperswitch-event",
                    true, "paid", sessionId, tenantId, userId);
        }
        return parsed;
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
