package com.example.platform.payment.infrastructure;

import com.example.platform.payment.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin Stripe Checkout Session client (no official SDK). Active when {@code platform.payment.stripe.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "platform.payment.stripe", name = "enabled", havingValue = "true")
public class StripeHttpPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeHttpPaymentProvider.class);

    private final StripePaymentProperties properties;
    private final HttpClient httpClient;

    public StripeHttpPaymentProvider(StripePaymentProperties properties) {
        this(properties, HttpClient.newHttpClient());
    }

    /** Package-private constructor for testing — allows injecting a mock/stub HttpClient. */
    StripeHttpPaymentProvider(StripePaymentProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public ProviderCode code() {
        return new ProviderCode("stripe");
    }

    @Override
    public CheckoutResult createCheckout(InitiateCheckoutCommand command) {
        long amount = command.amount().amountMinor();
        String success = command.successUrl() != null ? command.successUrl() : properties.getSuccessUrl();
        String cancel = command.cancelUrl() != null ? command.cancelUrl() : properties.getCancelUrl();

        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("success_url", success);
        form.put("cancel_url", cancel);
        form.put("client_reference_id", command.checkoutSessionId());
        form.put("metadata[checkout_session_id]", command.checkoutSessionId());
        form.put("metadata[tenant_id]", command.principal().tenantId());
        form.put("metadata[user_id]", command.principal().principalId());
        form.put("line_items[0][price_data][currency]",
                command.amount().currency().toLowerCase());
        form.put("line_items[0][price_data][unit_amount]", String.valueOf(Math.max(amount, 1L)));
        form.put("line_items[0][price_data][product_data][name]", command.productReference());
        form.put("line_items[0][quantity]", "1");

        try {
            String encoded = form.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Bearer " + properties.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(encoded))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Stripe HTTP " + response.statusCode() + ": " + response.body());
            }
            String body = response.body();
            String sessionId = extractJsonField(body, "id");
            String url = extractJsonField(body, "url");
            log.info("Stripe checkout session created ref={} for {}", sessionId, command.checkoutSessionId());
            return new CheckoutResult(sessionId != null ? sessionId : "stripe-unknown", url != null ? url : success);
        } catch (Exception e) {
            log.error("Stripe createCheckout failed for {}: {}", command.checkoutSessionId(), e.getMessage());
            throw new IllegalStateException("Stripe checkout session creation failed", e);
        }
    }

    @Override
    public PaymentVerificationResult verifyPayment(ProviderVerificationRequest command) {
        String ref = command.providerReference();
        if (ref == null || ref.isBlank()) {
            log.warn("Stripe verifyPayment called with blank providerReference");
            throw new IllegalArgumentException("providerReference is required");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions/" + ref))
                    .header("Authorization", "Bearer " + properties.getSecretKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Stripe verifyPayment HTTP {} for ref={}", response.statusCode(), ref);
                return new PaymentVerificationResult(false, "http_" + response.statusCode(), PaymentState.PENDING);
            }
            String body = response.body();
            String paymentStatus = extractJsonField(body, "payment_status");
            String sessionStatus = extractJsonField(body, "status");
            boolean paid = "paid".equalsIgnoreCase(paymentStatus)
                    && "complete".equalsIgnoreCase(sessionStatus);
            log.info("Stripe verifyPayment ref={} payment_status={} status={} verified={}",
                    ref, paymentStatus, sessionStatus, paid);
            return new PaymentVerificationResult(paid,
                    paymentStatus != null ? paymentStatus : "unknown",
                    paid ? PaymentState.SETTLED : PaymentState.PENDING);
        } catch (Exception e) {
            log.warn("Stripe verifyPayment failed for ref={}: {}", ref, e.getMessage());
            return new PaymentVerificationResult(false, "error", PaymentState.PENDING);
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest command) {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("payment_intent", command.originalCaptureReference());
        form.put("amount", Long.toString(command.amount().amountMinor()));
        try {
            String encoded = form.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/refunds"))
                    .header("Authorization", "Bearer " + properties.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(encoded)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return new ProviderRefundResult(false, null, "http_" + response.statusCode());
            return new ProviderRefundResult(true, extractJsonField(response.body(), "id"),
                    extractJsonField(response.body(), "status"));
        } catch (Exception failure) {
            throw new IllegalStateException("Stripe refund failed", failure);
        }
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        return WebhookPayloadSupport.parseCommerceWebhook(body);
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

    private static String extractMetadata(String body, String key) {
        if (body == null) {
            return null;
        }
        String needle = "\"metadata\"";
        if (!body.contains(needle) && !body.contains(key)) {
            return null;
        }
        String pattern = "\"" + key + "\":\"";
        int idx = body.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int start = idx + pattern.length();
        int end = body.indexOf('"', start);
        return end > start ? body.substring(start, end) : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
