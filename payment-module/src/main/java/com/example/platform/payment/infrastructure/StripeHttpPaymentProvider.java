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
    public CheckoutResult createCheckout(CheckoutCommand command) {
        long amount = command.amountMinor() != null ? command.amountMinor() : 0L;
        String success = command.successUrl() != null ? command.successUrl() : properties.getSuccessUrl();
        String cancel = command.cancelUrl() != null ? command.cancelUrl() : properties.getCancelUrl();

        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("success_url", success);
        form.put("cancel_url", cancel);
        form.put("client_reference_id", command.checkoutSessionId());
        form.put("metadata[checkout_session_id]", command.checkoutSessionId());
        form.put("metadata[tenant_id]", command.tenantId() != null ? command.tenantId() : "");
        form.put("metadata[user_id]", command.userId() != null ? command.userId() : "");
        form.put("line_items[0][price_data][currency]",
                command.currencyCode() != null ? command.currencyCode().toLowerCase() : "usd");
        form.put("line_items[0][price_data][unit_amount]", String.valueOf(Math.max(amount, 1L)));
        form.put("line_items[0][price_data][product_data][name]", command.canonicalProductCode());
        form.put("line_items[0][quantity]", "1");

        try {
            String encoded = form.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                    .header("Authorization", "Bearer " + properties.getSecretKey())
                    .header("Content-Type", "application/x-www-form-urlencoded")
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
    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        String ref = command.providerReference();
        if (ref == null || ref.isBlank()) {
            log.warn("Stripe verifyPayment called with blank providerReference");
            return new PaymentVerificationResult(false, "missing_reference", "unknown");
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
                return new PaymentVerificationResult(false, "http_" + response.statusCode(), "unknown");
            }
            String body = response.body();
            String paymentStatus = extractJsonField(body, "payment_status");
            String sessionStatus = extractJsonField(body, "status");
            boolean paid = "paid".equalsIgnoreCase(paymentStatus)
                    && "complete".equalsIgnoreCase(sessionStatus);
            String canonical = paid ? "paid" : "pending";
            log.info("Stripe verifyPayment ref={} payment_status={} status={} verified={}",
                    ref, paymentStatus, sessionStatus, paid);
            return new PaymentVerificationResult(paid,
                    paymentStatus != null ? paymentStatus : "unknown",
                    canonical);
        } catch (Exception e) {
            log.warn("Stripe verifyPayment failed for ref={}: {}", ref, e.getMessage());
            return new PaymentVerificationResult(false, "error", "unknown");
        }
    }

    @Override
    public WebhookParseResult parseWebhook(Map<String, String> headers, String body) {
        WebhookParseResult parsed = WebhookPayloadSupport.parseCommerceWebhook(body, "stripe-webhook");
        if (parsed.checkoutSessionId() != null) {
            return parsed;
        }
        String sessionId = extractMetadata(body, "checkout_session_id");
        String tenantId = extractMetadata(body, "tenant_id");
        String userId = extractMetadata(body, "user_id");
        String ref = extractJsonField(body, "id");
        if (body != null && body.contains("checkout.session.completed")) {
            return new WebhookParseResult(
                    "payment.succeeded", 1,
                    ref != null ? ref : "stripe-event",
                    true, "paid", sessionId, tenantId, userId);
        }
        return parsed;
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
