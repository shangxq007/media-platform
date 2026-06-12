package com.example.platform.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class SafeDownloadUrlValidatorTest {

    @AfterEach
    void tearDown() {
        SafeDownloadUrlValidator.resetDnsResolver();
    }

    @Test
    void shouldAllowPublicHttpsUrl() {
        // Use a fake resolver that returns a public IP
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34")
        });
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

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.0.2", "127.255.255.255"})
    void shouldRejectLoopbackIpv4(String ip) {
        assertNotNull(SafeDownloadUrlValidator.validate("http://" + ip + "/a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "172.16.0.1", "192.168.1.1"})
    void shouldRejectPrivateIpv4(String ip) {
        assertNotNull(SafeDownloadUrlValidator.validate("http://" + ip + "/a"));
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

    // --- DnsResolver injection tests ---

    @Test
    void shouldRejectHostnameResolvingToPrivateIp() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("10.0.0.1")
        });
        String result = SafeDownloadUrlValidator.validate("https://evil.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("10.0.0.1"), "Should mention the resolved IP");
    }

    @Test
    void shouldRejectHostnameResolvingToLoopback() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("127.0.0.1")
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://evil.example.com/file"));
    }

    @Test
    void shouldRejectHostnameResolvingToLinkLocal() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("169.254.1.1")
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://evil.example.com/file"));
    }

    @Test
    void shouldRejectWhenMultipleIpsIncludePrivate() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34"),  // public
                InetAddress.getByName("192.168.1.1")     // private
        });
        String result = SafeDownloadUrlValidator.validate("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("192.168.1.1"), "Should mention the private IP");
    }

    @Test
    void shouldRejectWhenDnsFails() {
        SafeDownloadUrlValidator.setDnsResolver(host -> {
            throw new UnknownHostException("DNS lookup failed");
        });
        String result = SafeDownloadUrlValidator.validate("https://unresolvable.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("DNS resolution failed"));
    }

    @Test
    void shouldRejectWhenDnsReturnsEmpty() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[0]);
        String result = SafeDownloadUrlValidator.validate("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("no addresses"));
    }

    @Test
    void fakeResolverDoesNotRequireGlobalSwitch() {
        // Verify that we can test without any global mutable state
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34")
        });
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file"));

        // Change resolver — behavior changes immediately
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                InetAddress.getByName("10.0.0.1")
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://example.com/file"));
    }

    @Test
    void shouldRejectCarrierGradeNat() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://100.64.0.1/a"));
        assertNotNull(SafeDownloadUrlValidator.validate("http://100.127.255.255/a"));
    }

    @Test
    void shouldRejectBenchmarkingRange() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://198.18.0.1/a"));
        assertNotNull(SafeDownloadUrlValidator.validate("http://198.19.255.255/a"));
    }

    @Test
    void shouldRejectUniqueLocalIpv6() {
        assertNotNull(SafeDownloadUrlValidator.validate("http://[fc00::1]/a"));
    }

    @Test
    void resetDnsResolverRestoresDefault() {
        SafeDownloadUrlValidator.setDnsResolver(host -> {
            throw new RuntimeException("should not be called");
        });
        SafeDownloadUrlValidator.resetDnsResolver();
        // After reset, the default resolver is active — this will do real DNS
        // We just verify it doesn't throw from our fake resolver
        assertNotNull(SafeDownloadUrlValidator.validate("http://localhost/a"));
    }
}
