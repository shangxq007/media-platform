package com.example.platform.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HyperswitchWebhookSignatureVerifier {

    private HyperswitchWebhookSignatureVerifier() {}

    public static boolean verify(Map<String, String> headers, String body, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank() || body == null) {
            return false;
        }
        String signatureHeader = headerValue(headers, "X-Hmac-SHA256");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            String expected = hmacHex(webhookSecret, body);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
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
}
