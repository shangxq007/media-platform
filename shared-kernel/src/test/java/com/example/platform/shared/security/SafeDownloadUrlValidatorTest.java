package com.example.platform.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeDownloadUrlValidatorTest {

    @Test
    void shouldAllowPublicHttpsUrl() {
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file.mp4"));
        assertTrue(SafeDownloadUrlValidator.isSafe("https://example.com/file.mp4"));
    }

    @Test
    void shouldRejectFileScheme() {
        assertNotNull(SafeDownloadUrlValidator.validate("file:///etc/passwd"));
    }

    @Test
    void shouldRejectLocalhost() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://localhost:8080/a"));
    }

    @Test
    void shouldRejectLoopbackIpv4() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://127.0.0.1/a"));
        assertNotNull(SafeDownloadUrlValidator.validate("http://127.0.0.2/a"));
    }

    @Test
    void shouldRejectPrivateIpv4() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://10.0.0.1/a"));
        assertNotNull(SafeDownloadUrlValidator.validate("http://172.16.0.1/a"));
        assertNotNull(SafeDownloadUrlValidator.validate("http://192.168.1.1/a"));
    }

    @Test
    void shouldRejectLinkLocal() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void shouldRejectNullAndBlank() {
        assertNotNull(SafeDownloadUrlValidator.validate(null));
        assertNotNull(SafeDownloadUrlValidator.validate(""));
        assertNotNull(SafeDownloadUrlValidator.validate("   "));
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertNotNull(SafeDownloadUrlValidator.validate("not-a-url"));
        assertNotNull(SafeDownloadUrlValidator.validate("http:///bad"));
    }

    @Test
    void shouldRejectFtpScheme() {
        assertNotNull(SafeDownloadUrlValidator.validate("ftp://example.com/file"));
    }

    @Test
    void shouldRejectTooLongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(9000);
        assertNotNull(SafeDownloadUrlValidator.validate(longUrl));
    }
}
