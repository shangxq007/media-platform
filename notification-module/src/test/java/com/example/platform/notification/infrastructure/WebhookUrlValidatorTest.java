package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookUrlValidatorTest {

    private static final ConfigurableErrorCode INVALID_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-006", 4002006,
            Map.of("en", "Invalid webhook URL", "zh", "Webhook URL 无效"),
            "notification", 400);

    private static final ConfigurableErrorCode BLOCKED_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-403-001", 4032001,
            Map.of("en", "Webhook URL resolved to private/internal IP and was blocked",
                    "zh", "Webhook URL 解析到内部/私有 IP，已被阻止"),
            "notification", 403);

    private WebhookUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WebhookUrlValidator(List.of(), List.of());
    }

    @Test
    void rejectLocalhost() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://localhost:8080/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void rejectLoopback127x() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://127.0.0.1:8080/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void rejectLoopback127Range() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://127.1.2.3/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject10xPrivate() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://10.0.0.1/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject10xFullRange() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://10.255.255.255/webhook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject172xPrivateRange() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://172.16.0.1/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject172xUpperPrivate() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://172.31.255.255/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject192168Private() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://192.168.0.1/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void reject192168FullRange() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://192.168.255.255/webhook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void rejectLinkLocal169254() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://169.254.169.254/latest/meta-data", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void rejectLinkLocal169254Variants() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://169.254.1.1/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void rejectFileScheme() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("file:///etc/passwd", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-400-006", ex.getErrorCode().code());
    }

    @Test
    void rejectFtpScheme() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("ftp://example.com/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-400-006", ex.getErrorCode().code());
    }

    @Test
    void rejectNonHttpSchemes() {
        String[] schemes = {"ssh", "telnet", "gopher", "ws", "wss"};
        for (String scheme : schemes) {
            PlatformException ex = assertThrows(PlatformException.class, () ->
                    validator.validate(scheme + "://example.com/hook", INVALID_ERROR, BLOCKED_ERROR));
            assertEquals("NOTIFICATION-400-006", ex.getErrorCode().code(),
                    "Scheme " + scheme + " should be rejected as invalid");
        }
    }

    @Test
    void rejectMalformedUrl() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("not-a-url-at-all", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-400-006", ex.getErrorCode().code());
    }

    @Test
    void publicUrlAllowedWhenDnsResolvesToPublicIp() {
        assertDoesNotThrow(() ->
                validator.validate("http://203.0.113.1/webhook", INVALID_ERROR, BLOCKED_ERROR));
    }

    @Test
    void publicHttpsUrlAllowedWhenDnsResolvesToPublicIp() {
        assertDoesNotThrow(() ->
                validator.validate("https://203.0.113.50/notify", INVALID_ERROR, BLOCKED_ERROR));
    }

    @Test
    void publicUrlWithPortAllowedWhenDnsResolvesToPublicIp() {
        assertDoesNotThrow(() ->
                validator.validate("https://203.0.113.100:8443/webhook", INVALID_ERROR, BLOCKED_ERROR));
    }

    @Test
    void blocklistRejectsMatchingHost() {
        WebhookUrlValidator blocklistValidator = new WebhookUrlValidator(
                List.of(), List.of("evil.example.com"));

        PlatformException ex = assertThrows(PlatformException.class, () ->
                blocklistValidator.validate("https://evil.example.com/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void blocklistRejectsSubdomainMatch() {
        WebhookUrlValidator blocklistValidator = new WebhookUrlValidator(
                List.of(), List.of("evil.example.com"));

        PlatformException ex = assertThrows(PlatformException.class, () ->
                blocklistValidator.validate("https://sub.evil.example.com/hook", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void blocklistAllowsNonMatchingHost() {
        WebhookUrlValidator blocklistValidator = new WebhookUrlValidator(
                List.of(), List.of("evil.example.com"));

        assertDoesNotThrow(() ->
                blocklistValidator.validate("https://203.0.113.1/hook", INVALID_ERROR, BLOCKED_ERROR));
    }

    @Test
    void rejectUrlWithNoHost() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http:///path-only", INVALID_ERROR, BLOCKED_ERROR));
        assertEquals("NOTIFICATION-400-006", ex.getErrorCode().code());
    }

    @Test
    void blockedErrorHasCorrectI18n() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                validator.validate("http://127.0.0.1/hook", INVALID_ERROR, BLOCKED_ERROR));

        assertEquals("Webhook URL resolved to private/internal IP and was blocked",
                ex.getLocalizedMessage());
    }
}
