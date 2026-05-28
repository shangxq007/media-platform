package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for WebhookUrlValidator SSRF protection and allowlist logic.
 */
class WebhookUrlValidatorTest {

    // ==================== Scheme / Parse ====================

    @Test
    void rejectsNullUrl() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate(null));
    }

    @Test
    void rejectsBlankUrl() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("  "));
    }

    @Test
    void rejectsMalformedUrl() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("not a url at all"));
    }

    @Test
    void rejectsFtpScheme() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("ftp://example.com/webhook"));
    }

    @Test
    void rejectsFileScheme() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("file:///etc/passwd"));
    }

    @Test
    void acceptsHttpPublicHost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertDoesNotThrow(() -> v.validate("http://alerts.example.com/webhook"));
    }

    @Test
    void acceptsHttpsPublicHost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertDoesNotThrow(() -> v.validate("https://alerts.example.com/webhook"));
    }

    @Test
    void rejectsUserinfo() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("https://user:pass@alerts.example.com/webhook"));
    }

    @Test
    void rejectsEmptyHost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http:///webhook"));
    }

    // ==================== Localhost / Loopback ====================

    @Test
    void rejectsLocalhost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost/webhook"));
    }

    @Test
    void rejectsLocalhostUppercase() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://LOCALHOST/webhook"));
    }

    @Test
    void rejectsLoopbackIp() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://127.0.0.1/webhook"));
    }

    @Test
    void rejectsLoopbackRange() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://127.10.20.30/webhook"));
    }

    @Test
    void rejectsIpv6Loopback() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://[::1]/webhook"));
    }

    @Test
    void rejectsAnyIpv4() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://0.0.0.0/webhook"));
    }

    // ==================== Metadata / Link-local ====================

    @Test
    void rejectsMetadataIp() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void rejectsLinkLocalIp() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://169.254.1.1/webhook"));
    }

    // ==================== Private networks ====================

    @Test
    void rejects10Network() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://10.0.0.1/webhook"));
    }

    @Test
    void rejects172_16Network() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://172.16.0.1/webhook"));
    }

    @Test
    void rejects172_31Network() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://172.31.255.255/webhook"));
    }

    @Test
    void rejects192_168Network() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://192.168.1.10/webhook"));
    }

    @Test
    void acceptsPrivateIpWhenAllowed() {
        WebhookUrlValidator v = new WebhookUrlValidator(true, List.of(), List.of());
        assertDoesNotThrow(() -> v.validate("http://10.0.0.1/webhook"));
    }

    @Test
    void rejectsMetadataEvenWhenPrivateAllowed() {
        WebhookUrlValidator v = new WebhookUrlValidator(true, List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void rejectsLocalhostEvenWhenPrivateAllowed() {
        WebhookUrlValidator v = new WebhookUrlValidator(true, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://localhost/webhook"));
    }

    @Test
    void rejectsLoopbackEvenWhenPrivateAllowed() {
        WebhookUrlValidator v = new WebhookUrlValidator(true, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> v.validate("http://127.0.0.1/webhook"));
    }

    // ==================== Allowlist ====================

    @Test
    void acceptsAllowedHost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of("alerts.example.com"), List.of());
        assertDoesNotThrow(() -> v.validate("https://alerts.example.com/webhook"));
    }

    @Test
    void acceptsAllowedHostCaseInsensitive() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of("alerts.example.com"), List.of());
        assertDoesNotThrow(() -> v.validate("https://ALERTS.EXAMPLE.COM/webhook"));
    }

    @Test
    void rejectsNonAllowedHost() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of("alerts.example.com"), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("https://evil.example.com/webhook"));
    }

    @Test
    void acceptsSuffixMatch() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of(), List.of(".alerts.example.com"));
        assertDoesNotThrow(() -> v.validate("https://foo.alerts.example.com/webhook"));
    }

    @Test
    void rejectsSuffixWithoutLeadingDot() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of(), List.of("alerts.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("https://foo.alerts.example.com/webhook"));
    }

    @Test
    void rejectsEvilSuffixAttack() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of(), List.of(".alerts.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("https://evilalerts.example.com/webhook"));
    }

    @Test
    void rejectsBareSuffixDomain() {
        // "alerts.example.com" alone should not match suffix ".alerts.example.com"
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of(), List.of(".alerts.example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> v.validate("https://alerts.example.com/webhook"));
    }

    @Test
    void normalizesTrailingDot() {
        WebhookUrlValidator v = new WebhookUrlValidator(false,
                List.of("alerts.example.com"), List.of());
        assertDoesNotThrow(() -> v.validate("https://alerts.example.com./webhook"));
    }

    @Test
    void acceptsPublicIpWhenNoAllowlist() {
        WebhookUrlValidator v = new WebhookUrlValidator(false, List.of(), List.of());
        assertDoesNotThrow(() -> v.validate("http://93.184.216.34/webhook"));
    }

    // ==================== extractHost ====================

    @Test
    void extractHost_returnsHost() {
        assertEquals("alerts.example.com",
                WebhookUrlValidator.extractHost("https://alerts.example.com/webhook?q=secret"));
    }

    @Test
    void extractHost_includesNonStandardPort() {
        assertEquals("alerts.example.com:8443",
                WebhookUrlValidator.extractHost("https://alerts.example.com:8443/webhook"));
    }

    @Test
    void extractHost_excludesStandardPorts() {
        assertEquals("alerts.example.com",
                WebhookUrlValidator.extractHost("https://alerts.example.com:443/webhook"));
    }
}
