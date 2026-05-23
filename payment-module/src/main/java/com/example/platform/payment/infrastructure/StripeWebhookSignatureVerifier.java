package com.example.platform.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stripe webhook signature verification (v1 HMAC-SHA256).
 */
public final class StripeWebhookSignatureVerifier {

    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    private StripeWebhookSignatureVerifier() {}

    public static boolean verify(Map<String, String> headers, String body, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank() || body == null) {
            return false;
        }
        String signatureHeader = headerValue(headers, "Stripe-Signature");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String timestamp = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("t=")) {
                timestamp = trimmed.substring(2);
            } else if (trimmed.startsWith("v1=")) {
                v1 = trimmed.substring(3);
            }
        }
        if (timestamp == null || v1 == null) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000L;
            if (Math.abs(now - ts) > MAX_CLOCK_SKEW_SECONDS) {
                return false;
            }
            String payload = timestamp + "." + body;
            String expected = hmacHex(webhookSecret, payload);
            return constantTimeEquals(expected, v1);
        } catch (Exception e) {
            return false;
        }
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String hmacHex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
