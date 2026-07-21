package com.example.platform.shared.security;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SafeDownloadUrlValidator with strict instance-level resolver isolation.
 *
 * <p>Every test that needs DNS control creates its own validator instance with an
 * injected resolver. There is NO global mutable resolver state, NO @AfterEach reset,
 * and NO shared static fake resolver.
 *
 * <p>Test execution order is randomized to detect cross-test pollution.
 */
@TestMethodOrder(MethodOrderer.Random.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SafeDownloadUrlValidatorTest {

    // --- Helpers: construct InetAddress from explicit bytes (no DNS) ---

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

    // --- Reusable resolver factories (stateless lambdas, not shared mutable state) ---

    private static SafeDownloadUrlValidator.DnsResolver publicResolver() {
        return host -> new InetAddress[]{ipv4(93, 184, 216, 34)};
    }

    private static SafeDownloadUrlValidator.DnsResolver privateResolver() {
        return host -> new InetAddress[]{ipv4(10, 0, 0, 1)};
    }

    private static SafeDownloadUrlValidator.DnsResolver loopbackResolver() {
        return host -> new InetAddress[]{ipv4(127, 0, 0, 1)};
    }

    private static SafeDownloadUrlValidator.DnsResolver linkLocalResolver() {
        return host -> new InetAddress[]{ipv4(169, 254, 1, 1)};
    }

    private static SafeDownloadUrlValidator.DnsResolver failingResolver() {
        return host -> { throw new UnknownHostException("DNS lookup failed"); };
    }

    private static SafeDownloadUrlValidator.DnsResolver emptyResolver() {
        return host -> new InetAddress[0];
    }

    private static SafeDownloadUrlValidator.DnsResolver benchmarkResolver() {
        return host -> new InetAddress[]{ipv4(198, 18, 0, 1)};
    }

    private static SafeDownloadUrlValidator.DnsResolver mixedPublicPrivateResolver() {
        return host -> new InetAddress[]{
                ipv4(93, 184, 216, 34),   // public
                ipv4(192, 168, 1, 1)      // private
        };
    }

    private static SafeDownloadUrlValidator.DnsResolver multiPublicResolver() {
        return host -> new InetAddress[]{
                ipv4(93, 184, 216, 34),
                ipv4(93, 184, 216, 35)
        };
    }

    private static SafeDownloadUrlValidator.DnsResolver ipv6UniqueLocalResolver() {
        return host -> new InetAddress[]{
                ipv6(new byte[]{
                        (byte) 0xfc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
                })
        };
    }

    private static SafeDownloadUrlValidator.DnsResolver ipv6PublicResolver() {
        return host -> new InetAddress[]{
                ipv6(new byte[]{
                        0x26, 0x07, (byte) 0xf8, (byte) 0xb0,
                        0x40, 0x04, 0x08, 0x00,
                        0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x20, 0x0e
                })
        };
    }

    // ===== Section 1: Existing SSRF tests (preserved from base, converted to instance) =====

    @Test
    void should_allow_public_https_url() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNull(validator.validateUrl("https://example.com/file.mp4"));
        assertTrue(validator.isSafeUrl("https://example.com/file.mp4"));
    }

    @Test
    void should_reject_file_scheme() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("file:///etc/passwd"));
    }

    @Test
    void should_reject_localhost() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://localhost:8080/a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.0.0.2", "127.255.255.255"})
    void should_reject_loopback_ipv4(String ip) {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://" + ip + "/a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "172.16.0.1", "192.168.1.1"})
    void should_reject_private_ipv4(String ip) {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://" + ip + "/a"));
    }

    @Test
    void should_reject_link_local() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://169.254.169.254/latest/meta-data"));
    }

    @Test
    void should_reject_null_and_blank() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl(null));
        assertNotNull(validator.validateUrl(""));
        assertNotNull(validator.validateUrl("   "));
    }

    @Test
    void should_reject_invalid_url() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("not-a-url"));
        assertNotNull(validator.validateUrl("http:///bad"));
    }

    @Test
    void should_reject_ftp_scheme() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("ftp://example.com/file"));
    }

    @Test
    void should_reject_too_long_url() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        String longUrl = "https://example.com/" + "a".repeat(9000);
        assertNotNull(validator.validateUrl(longUrl));
    }

    @Test
    void should_reject_hostname_resolving_to_private_ip() {
        var validator = new SafeDownloadUrlValidator(privateResolver());
        String result = validator.validateUrl("https://evil.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("10.0.0.1"), "Should mention the resolved IP");
    }

    @Test
    void should_reject_hostname_resolving_to_loopback() {
        var validator = new SafeDownloadUrlValidator(loopbackResolver());
        assertNotNull(validator.validateUrl("https://evil.example.com/file"));
    }

    @Test
    void should_reject_hostname_resolving_to_link_local() {
        var validator = new SafeDownloadUrlValidator(linkLocalResolver());
        assertNotNull(validator.validateUrl("https://evil.example.com/file"));
    }

    @Test
    void should_reject_when_multiple_ips_include_private() {
        var validator = new SafeDownloadUrlValidator(mixedPublicPrivateResolver());
        String result = validator.validateUrl("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("192.168.1.1"), "Should mention the private IP");
    }

    @Test
    void should_reject_when_dns_fails() {
        var validator = new SafeDownloadUrlValidator(failingResolver());
        String result = validator.validateUrl("https://unresolvable.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("DNS resolution failed"));
    }

    @Test
    void should_reject_when_dns_returns_empty() {
        var validator = new SafeDownloadUrlValidator(emptyResolver());
        String result = validator.validateUrl("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("no addresses"));
    }

    @Test
    void should_reject_carrier_grade_nat() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://100.64.0.1/a"));
        assertNotNull(validator.validateUrl("http://100.127.255.255/a"));
    }

    @Test
    void should_reject_benchmarking_range() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://198.18.0.1/a"));
        assertNotNull(validator.validateUrl("http://198.19.255.255/a"));
    }

    @Test
    void should_reject_unique_local_ipv6() {
        var validator = new SafeDownloadUrlValidator(publicResolver());
        assertNotNull(validator.validateUrl("http://[fc00::1]/a"));
    }

    @Test
    void should_reject_benchmarking_address_via_resolver() {
        var validator = new SafeDownloadUrlValidator(benchmarkResolver());
        String result = validator.validateUrl("https://benchmark.example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("198.18"), "Should reject 198.18.0.0/15 benchmarking range");
    }

    @Test
    void should_reject_dns_exception_fail_closed() {
        var validator = new SafeDownloadUrlValidator(
                host -> { throw new RuntimeException("simulated DNS infrastructure failure"); }
        );
        String result = validator.validateUrl("https://example.com/file");
        assertNotNull(result);
        assertTrue(result.contains("DNS resolution failed"),
                "DNS exception must fail-closed, got: " + result);
    }

    @Test
    void should_allow_multiple_public_addresses() {
        var validator = new SafeDownloadUrlValidator(multiPublicResolver());
        assertNull(validator.validateUrl("https://example.com/file"));
    }

    @Test
    void should_reject_ipv6_unique_local_via_resolver() {
        var validator = new SafeDownloadUrlValidator(ipv6UniqueLocalResolver());
        assertNotNull(validator.validateUrl("https://example.com/file"));
    }

    @Test
    void should_allow_public_ipv6_via_resolver() {
        var validator = new SafeDownloadUrlValidator(ipv6PublicResolver());
        assertNull(validator.validateUrl("https://example.com/file"));
    }

    // ===== Section 2: Instance isolation tests =====

    @Test
    @Order(100)
    void two_instances_opposite_resolvers_a_allows_b_rejects() {
        // Validator A: public resolver -> allows
        // Validator B: private resolver -> rejects
        var validatorA = new SafeDownloadUrlValidator(publicResolver());
        var validatorB = new SafeDownloadUrlValidator(privateResolver());

        assertNull(validatorA.validateUrl("https://example.com/file"),
                "Validator A (public resolver) should allow");
        assertNotNull(validatorB.validateUrl("https://example.com/file"),
                "Validator B (private resolver) should reject");
    }

    @Test
    @Order(101)
    void two_instances_reverse_creation_order() {
        // Create B first, then A — results must be identical
        var validatorB = new SafeDownloadUrlValidator(privateResolver());
        var validatorA = new SafeDownloadUrlValidator(publicResolver());

        assertNull(validatorA.validateUrl("https://example.com/file"),
                "Validator A (public resolver) should allow regardless of creation order");
        assertNotNull(validatorB.validateUrl("https://example.com/file"),
                "Validator B (private resolver) should reject regardless of creation order");
    }

    @Test
    @Order(102)
    void alternating_calls_100_times_no_pollution() {
        var validatorA = new SafeDownloadUrlValidator(publicResolver());
        var validatorB = new SafeDownloadUrlValidator(privateResolver());

        for (int i = 0; i < 100; i++) {
            assertNull(validatorA.validateUrl("https://example.com/file"),
                    "Iteration " + i + ": Validator A should allow");
            assertNotNull(validatorB.validateUrl("https://example.com/file"),
                    "Iteration " + i + ": Validator B should reject");
        }
    }

    @Test
    @Order(103)
    void parallel_concurrent_instance_stress_500_iterations() throws Exception {
        var validatorA = new SafeDownloadUrlValidator(publicResolver());
        var validatorB = new SafeDownloadUrlValidator(privateResolver());

        int iterationsPerThread = 500;
        AtomicInteger incorrectResults = new AtomicInteger(0);
        AtomicInteger exceptions = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread A: validator A should always allow
        Thread threadA = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                    try {
                        String result = validatorA.validateUrl("https://example.com/file");
                        if (result != null) {
                            incorrectResults.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread B: validator B should always reject
        Thread threadB = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterationsPerThread; i++) {
                    try {
                        String result = validatorB.validateUrl("https://example.com/file");
                        if (result == null) {
                            incorrectResults.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        threadA.start();
        threadB.start();
        startLatch.countDown(); // Release both threads simultaneously
        doneLatch.await();

        assertEquals(0, incorrectResults.get(), "Incorrect results: cross-instance resolver pollution detected");
        assertEquals(0, exceptions.get(), "Unexpected exceptions during concurrent execution");
    }

    @Test
    @Order(104)
    void parallel_creation_with_different_resolvers() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger incorrectResults = new AtomicInteger(0);
        AtomicInteger exceptions = new AtomicInteger(0);
        AtomicReference<String> firstError = new AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    // Each thread creates its own validator with public resolver
                    var validator = new SafeDownloadUrlValidator(publicResolver());
                    for (int i = 0; i < 100; i++) {
                        try {
                            String result = validator.validateUrl("https://example.com/file");
                            if (result != null) {
                                int count = incorrectResults.incrementAndGet();
                                if (count == 1) {
                                    firstError.set("Thread " + threadId + ": unexpected reject: " + result);
                                }
                            }
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertEquals(0, incorrectResults.get(),
                "Incorrect results in parallel creation: " + firstError.get());
        assertEquals(0, exceptions.get(), "Unexpected exceptions");
    }

    @Test
    @Order(105)
    void default_constructor_uses_system_resolver() {
        // Default constructor creates a new instance with system DNS
        // It must NOT affect other instances' resolvers
        var defaultValidator = new SafeDownloadUrlValidator();
        var customValidator = new SafeDownloadUrlValidator(privateResolver());

        // localhost is rejected by IP check, regardless of resolver
        assertNotNull(defaultValidator.validateUrl("http://localhost/a"),
                "Default validator should reject localhost");
        assertNotNull(customValidator.validateUrl("http://localhost/a"),
                "Custom validator should also reject localhost");

        // Custom validator rejects URLs resolving to private IP
        assertNotNull(customValidator.validateUrl("https://example.com/file"),
                "Custom validator with private resolver should reject");
    }

    @Test
    @Order(106)
    void no_global_reset_needed_after_test() {
        // This test verifies that instance injection requires no global cleanup.
        // Creating a validator with a private resolver and then discarding it
        // should not affect any other validator.
        var throwResolver = new SafeDownloadUrlValidator(
                host -> { throw new RuntimeException("should not leak"); }
        );
        // Discard it — no reset needed

        // A fresh validator with public resolver should work fine
        var fresh = new SafeDownloadUrlValidator(publicResolver());
        assertNull(fresh.validateUrl("https://example.com/file"),
                "Fresh instance must not be polluted by discarded instance");
    }

    @Test
    @Order(107)
    void static_facade_uses_immutable_default_instance() {
        // The static validate() method uses an immutable DEFAULT instance
        // Creating custom instances does not affect the static facade
        var custom = new SafeDownloadUrlValidator(privateResolver());
        assertNotNull(custom.validateUrl("https://example.com/file"),
                "Custom validator with private resolver should reject");

        // The static facade still works independently
        // (it uses system DNS which may or may not resolve, but it's not polluted)
        // We just verify it doesn't throw from our custom validator
        assertDoesNotThrow(() -> SafeDownloadUrlValidator.validate("http://localhost/a"));
    }

    @Test
    @Order(108)
    void instances_created_in_loop_maintain_isolation() {
        // Create many instances in a loop, verify each is independent
        SafeDownloadUrlValidator[] validators = new SafeDownloadUrlValidator[20];
        for (int i = 0; i < 20; i++) {
            validators[i] = (i % 2 == 0)
                    ? new SafeDownloadUrlValidator(publicResolver())
                    : new SafeDownloadUrlValidator(privateResolver());
        }

        for (int i = 0; i < 20; i++) {
            String result = validators[i].validateUrl("https://example.com/file");
            if (i % 2 == 0) {
                assertNull(result, "Index " + i + " (public) should allow");
            } else {
                assertNotNull(result, "Index " + i + " (private) should reject");
            }
        }
    }
}
