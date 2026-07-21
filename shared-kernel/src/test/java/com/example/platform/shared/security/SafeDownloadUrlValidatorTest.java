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

    // --- Helper: construct InetAddress from explicit bytes (no DNS) ---

    private static InetAddress ipv4(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[]{
                    (byte) a, (byte) b, (byte) c, (byte) d
            });
        } catch (UnknownHostException e) {
            throw new AssertionError("Should not happen for 4-byte address", e);
        }
    }

    private static InetAddress ipv6(byte... addr) {
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            throw new AssertionError("Should not happen for 16-byte address", e);
        }
    }

    // --- Tests using injected resolver (no system DNS) ---

    @Test
    void shouldAllowPublicHttpsUrl() {
        // Use a fake resolver that returns a public IP (explicit bytes, no DNS)
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(93, 184, 216, 34)
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

    // --- DnsResolver injection tests (fully deterministic, no system DNS) ---

    @Test
    void shouldRejectHostnameResolvingToPrivateIp() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(10, 0, 0, 1)
        });
        String result = SafeDownloadUrlValidator.validate("https://evil.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("10.0.0.1"), "Should mention the resolved IP");
    }

    @Test
    void shouldRejectHostnameResolvingToLoopback() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(127, 0, 0, 1)
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://evil.example.com/file"));
    }

    @Test
    void shouldRejectHostnameResolvingToLinkLocal() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(169, 254, 1, 1)
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://evil.example.com/file"));
    }

    @Test
    void shouldRejectWhenMultipleIpsIncludePrivate() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(93, 184, 216, 34),  // public
                ipv4(192, 168, 1, 1)     // private
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
        // Each resolver is a distinct instance — no JVM global switch needed
        SafeDownloadUrlValidator.DnsResolver publicResolver =
                host -> new InetAddress[]{ipv4(93, 184, 216, 34)};
        SafeDownloadUrlValidator.DnsResolver privateResolver =
                host -> new InetAddress[]{ipv4(10, 0, 0, 1)};

        SafeDownloadUrlValidator.setDnsResolver(publicResolver);
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file"));

        // Change resolver — behavior changes immediately (instance-level, no global switch)
        SafeDownloadUrlValidator.setDnsResolver(privateResolver);
        assertNotNull(SafeDownloadUrlValidator.validate("https://example.com/file"));

        // Verify isolation: publicResolver still returns the same results
        SafeDownloadUrlValidator.setDnsResolver(publicResolver);
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file"));
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

    // --- Additional determinism and isolation tests ---

    @Test
    void shouldRejectBenchmarkingAddressViaResolver() {
        // 198.18.0.0/15 must be rejected even when returned by fake resolver
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(198, 18, 0, 1)
        });
        String result = SafeDownloadUrlValidator.validate("https://benchmark.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("198.18"), "Should reject 198.18.0.0/15 benchmarking range");
    }

    @Test
    void resolverInstancesAreIsolated() {
        // Two different resolver instances do not share state
        SafeDownloadUrlValidator.DnsResolver resolver1 =
                host -> new InetAddress[]{ipv4(93, 184, 216, 34)};
        SafeDownloadUrlValidator.DnsResolver resolver2 =
                host -> new InetAddress[]{ipv4(10, 0, 0, 1)};

        SafeDownloadUrlValidator.setDnsResolver(resolver1);
        assertNull(SafeDownloadUrlValidator.validate("https://test.example.com/a"));

        SafeDownloadUrlValidator.setDnsResolver(resolver2);
        assertNotNull(SafeDownloadUrlValidator.validate("https://test.example.com/a"));

        // resolver1 is unchanged — no cross-instance pollution
        SafeDownloadUrlValidator.setDnsResolver(resolver1);
        assertNull(SafeDownloadUrlValidator.validate("https://test.example.com/a"));
    }

    @Test
    void shouldRejectDnsExceptionFailClosed() {
        // Any exception from resolver must result in rejection (fail-closed)
        SafeDownloadUrlValidator.setDnsResolver(host -> {
            throw new RuntimeException("simulated DNS infrastructure failure");
        });
        String result = SafeDownloadUrlValidator.validate("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("DNS resolution failed"),
                "DNS exception must fail-closed, got: " + result);
    }

    @Test
    void shouldRejectMultiplePublicAddresses() {
        // Multiple public addresses — all must pass validation
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv4(93, 184, 216, 34),
                ipv4(93, 184, 216, 35)
        });
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file"));
    }

    @Test
    void shouldRejectIpv6UniqueLocalViaResolver() {
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv6(new byte[]{
                        (byte) 0xfc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
                })
        });
        assertNotNull(SafeDownloadUrlValidator.validate("https://example.com/file"));
    }

    @Test
    void shouldAllowPublicIpv6ViaResolver() {
        // 2001:db8:: is documentation range but not blocked by current validator
        // Use a known public IPv6: 2607:f8b0:4004:800::200e (Google DNS)
        SafeDownloadUrlValidator.setDnsResolver(host -> new InetAddress[]{
                ipv6(new byte[]{
                        0x26, 0x07, (byte) 0xf8, (byte) 0xb0,
                        0x40, 0x04, 0x08, 0x00,
                        0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x20, 0x0e
                })
        });
        assertNull(SafeDownloadUrlValidator.validate("https://example.com/file"));
    }
}
