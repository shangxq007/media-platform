package com.example.platform.payment.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeWebhookSignatureVerifierTest {

    @Test
    void verifiesValidV1Signature() throws Exception {
        String secret = "whsec_test";
        String body = "{\"id\":\"evt_1\"}";
        long ts = System.currentTimeMillis() / 1000L;
        String payload = ts + "." + body;
        String sig = hmac(secret, payload);
        Map<String, String> headers = Map.of("Stripe-Signature", "t=" + ts + ",v1=" + sig);
        assertTrue(StripeWebhookSignatureVerifier.verify(headers, body, secret));
    }

    @Test
    void rejectsMissingSecret() {
        assertFalse(StripeWebhookSignatureVerifier.verify(Map.of(), "{}", ""));
    }

    private static String hmac(String secret, String payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
