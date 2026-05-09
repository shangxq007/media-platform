package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private WebhookSigner signer;

    @BeforeEach
    void setUp() {
        signer = new WebhookSigner("test-secret");
    }

    @Test
    void signReturnsV1PrefixedSignature() {
        String signature = signer.sign("1234567890", "{\"key\":\"value\"}");
        assertNotNull(signature);
        assertTrue(signature.startsWith("v1="), "Signature should start with v1=");
    }

    @Test
    void signProducesHexEncodedOutput() {
        String signature = signer.sign("ts1", "body1");
        String hash = signature.substring(3); // strip "v1="
        assertTrue(hash.matches("[0-9a-f]+"), "Hash part should be hex-encoded");
    }

    @Test
    void signProducesConsistentOutput() {
        String sig1 = signer.sign("ts1", "body1");
        String sig2 = signer.sign("ts1", "body1");
        assertEquals(sig1, sig2, "Same input should produce same signature");
    }

    @Test
    void signProducesDifferentOutputForDifferentTimestamps() {
        String sig1 = signer.sign("ts1", "body1");
        String sig2 = signer.sign("ts2", "body1");
        assertTrue(!sig1.equals(sig2), "Different timestamps should produce different signatures");
    }

    @Test
    void signProducesDifferentOutputForDifferentBodies() {
        String sig1 = signer.sign("ts1", "body1");
        String sig2 = signer.sign("ts1", "body2");
        assertTrue(!sig1.equals(sig2), "Different bodies should produce different signatures");
    }

    @Test
    void signWithDifferentSecretsProducesDifferentOutput() {
        WebhookSigner otherSigner = new WebhookSigner("different-secret");
        String sig1 = signer.sign("ts1", "body1");
        String sig2 = otherSigner.sign("ts1", "body1");
        assertTrue(!sig1.equals(sig2), "Different secrets should produce different signatures");
    }

    @Test
    void signWithEmptyBody() {
        String signature = signer.sign("ts1", "");
        assertNotNull(signature);
        assertTrue(signature.startsWith("v1="));
    }
}
