package com.example.platform.audit.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for WebhookSecurityAlertAdapter.
 *
 * <p>Tests are split into:
 * <ul>
 *   <li>Constructor validation (URL scheme, blank URL, etc.)</li>
 *   <li>Request body construction (verifies JSON serialization, sensitive field redaction)</li>
 *   <li>HTTP behavior (verified by integration tests; unit tests verify no-throw on errors)</li>
 * </ul>
 *
 * <p>The adapter swallows all HTTP errors (logs only), so HTTP round-trip verification
 * is done via integration tests, not unit tests with mock servers.
 */
class WebhookSecurityAlertAdapterTest {

    /**
     * Permissive validator for tests — accepts any http/https URL.
     * SSRF protection is tested separately in WebhookUrlValidatorTest.
     */
    private static WebhookUrlValidator permissiveValidator() {
        return new WebhookUrlValidator(true, List.of(), List.of());
    }

    private SecurityAlert sampleAlert() {
        return SecurityAlert.of(
                "SINGLE_DENIED", "HIGH", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS",
                "ADMIN", "admin-1", "tenant", null, "tenant-a", "DENIED", "req-1", "trace-1");
    }

    private WebhookSecurityAlertAdapter createAdapter(String url) {
        return new WebhookSecurityAlertAdapter(url, 1000, 1000, null, permissiveValidator());
    }

    private WebhookSecurityAlertAdapter createAdapter(String url, String authHeader) {
        return new WebhookSecurityAlertAdapter(url, 1000, 1000, authHeader, permissiveValidator());
    }

    // ==================== Constructor validation ====================

    @Test
    void constructor_rejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> createAdapter(""));
    }

    @Test
    void constructor_rejectsNullUrl() {
        assertThrows(IllegalArgumentException.class, () -> createAdapter(null));
    }

    @Test
    void constructor_rejectsInvalidScheme() {
        assertThrows(IllegalArgumentException.class, () -> createAdapter("ftp://example.com"));
    }

    @Test
    void constructor_acceptsHttpUrl() {
        assertDoesNotThrow(() -> createAdapter("http://example.com/alerts"));
    }

    @Test
    void constructor_acceptsHttpsUrl() {
        assertDoesNotThrow(() -> createAdapter("https://example.com/alerts"));
    }

    // ==================== SecurityAlert JSON serialization ====================

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Test
    void securityAlertSerialization_containsExpectedFields() throws Exception {
        ObjectMapper mapper = createObjectMapper();
        String json = mapper.writeValueAsString(sampleAlert());

        assertTrue(json.contains("SINGLE_DENIED"));
        assertTrue(json.contains("HIGH"));
        assertTrue(json.contains("ADMIN_AUDIT"));
        assertTrue(json.contains("admin-1"));
        assertTrue(json.contains("tenant-a"));
        assertTrue(json.contains("DENIED"));
        assertTrue(json.contains("req-1"));
        assertTrue(json.contains("trace-1"));
    }

    @Test
    void securityAlertSerialization_doesNotContainPayload() throws Exception {
        ObjectMapper mapper = createObjectMapper();
        String json = mapper.writeValueAsString(sampleAlert());

        assertFalse(json.contains("payload"), "SecurityAlert should not contain payload field");
    }

    @Test
    void securityAlertSerialization_redactsSensitiveAttributes() throws Exception {
        ObjectMapper mapper = createObjectMapper();
        Map<String, Object> attrs = Map.of(
                "apiKey", "sk-secret-key",
                "token", "tok-secret",
                "password", "pw-secret",
                "signedUrl", "https://s3.amazonaws.com/signed?X-Amz-Signature=abc",
                "deniedCount", 5);
        SecurityAlert alert = SecurityAlert.withAttributes(
                "ADMIN_DENIED_BURST", "HIGH", "ADMIN_AUDIT", "ADMIN_DENIED_BURST",
                "ADMIN", "admin-1", "audit_record", null, null, "DENIED", "", "", attrs);

        String json = mapper.writeValueAsString(alert);

        assertFalse(json.contains("sk-secret-key"), "apiKey should be redacted");
        assertFalse(json.contains("tok-secret"), "token should be redacted");
        assertFalse(json.contains("pw-secret"), "password should be redacted");
        assertFalse(json.contains("X-Amz-Signature"), "signedUrl should be redacted");
        assertTrue(json.contains("[REDACTED]"), "Should contain [REDACTED] marker");
        assertTrue(json.contains("deniedCount"), "Non-sensitive attributes should be preserved");
    }

    // ==================== HTTP behavior (no-throw guarantee) ====================

    // Use example.com:1 for connection-refused tests — public hostname (passes SSRF validator),
    // port 1 gives connection refused (nothing listening), adapter swallows errors.

    @Test
    void publish_connectionRefusedDoesNotThrow() {
        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts");
        assertDoesNotThrow(() -> adapter.publish(sampleAlert()));
    }

    @Test
    void publish_withAuthorizationHeader_doesNotThrow() {
        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts", "Bearer sk-secret");
        assertDoesNotThrow(() -> adapter.publish(sampleAlert()));
    }

    @Test
    void publish_withNullAuthorizationHeader_doesNotThrow() {
        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts", null);
        assertDoesNotThrow(() -> adapter.publish(sampleAlert()));
    }

    @Test
    void publish_withEmptyAuthorizationHeader_doesNotThrow() {
        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts", "");
        assertDoesNotThrow(() -> adapter.publish(sampleAlert()));
    }

    @Test
    void publish_withAttributes_doesNotThrow() {
        Map<String, Object> attrs = Map.of(
                "apiKey", "sk-secret-key", "token", "tok-secret",
                "password", "pw-secret", "deniedCount", 5);
        SecurityAlert alert = SecurityAlert.withAttributes(
                "ADMIN_DENIED_BURST", "HIGH", "ADMIN_AUDIT", "ADMIN_DENIED_BURST",
                "ADMIN", "admin-1", "audit_record", null, null, "DENIED", "", "", attrs);

        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts");
        assertDoesNotThrow(() -> adapter.publish(alert));
    }

    @Test
    void publish_multipleAlerts_doesNotThrow() {
        WebhookSecurityAlertAdapter adapter = createAdapter("http://example.com:1/alerts");
        for (int i = 0; i < 5; i++) {
            SecurityAlert alert = SecurityAlert.of(
                    "SINGLE_DENIED", "HIGH", "ADMIN_AUDIT", "ADMIN_LIST_TENANTS",
                    "ADMIN", "admin-" + i, "tenant", null, "tenant-a", "DENIED",
                    "req-" + i, "trace-" + i);
            assertDoesNotThrow(() -> adapter.publish(alert));
        }
    }

    // ==================== SSRF integration (via WebhookUrlValidator) ====================

    @Test
    void constructor_rejectsLocalhostWithStrictValidator() {
        WebhookUrlValidator strictValidator = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookSecurityAlertAdapter(
                        "http://localhost/alerts", 1000, 1000, null, strictValidator));
    }

    @Test
    void constructor_rejectsMetadataIpWithStrictValidator() {
        WebhookUrlValidator strictValidator = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookSecurityAlertAdapter(
                        "http://169.254.169.254/latest/meta-data", 1000, 1000, null, strictValidator));
    }

    @Test
    void constructor_acceptsAllowedHost() {
        WebhookUrlValidator allowlistValidator = new WebhookUrlValidator(false,
                List.of("alerts.example.com"), List.of());
        assertDoesNotThrow(() -> new WebhookSecurityAlertAdapter(
                "https://alerts.example.com/webhook", 1000, 1000, null, allowlistValidator));
    }

    @Test
    void constructor_rejectsPrivateIpWithoutPermission() {
        WebhookUrlValidator strictValidator = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WebhookSecurityAlertAdapter(
                        "http://10.0.0.1/alerts", 1000, 1000, null, strictValidator));
    }
}
