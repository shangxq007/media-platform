package com.example.platform.extension.app;

import com.example.platform.extension.domain.ExtensionResourceLimits;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtensionResourceLimiterTest {

    private AuditPort auditPort;
    private ExtensionResourceLimiter limiter;

    @BeforeEach
    void setUp() {
        auditPort = mock(AuditPort.class);
        limiter = new ExtensionResourceLimiter(auditPort);
    }

    @Test
    void shouldRegisterLimits() {
        limiter.registerLimits("ext-1", ExtensionResourceLimits.DEFAULTS);
        ExtensionResourceLimits limits = limiter.getLimits("ext-1");

        assertEquals(ExtensionResourceLimits.DEFAULTS, limits);
    }

    @Test
    void shouldReturnDefaultLimitsForUnknown() {
        ExtensionResourceLimits limits = limiter.getLimits("unknown-ext");
        assertEquals(ExtensionResourceLimits.DEFAULTS, limits);
    }

    @Test
    void shouldAcquireAndRelease() {
        limiter.registerLimits("ext-1", ExtensionResourceLimits.DEFAULTS);

        ExtensionResourceLimiter.ExtensionCheckResult result = limiter.checkAndAcquire("ext-1", 100);
        assertTrue(result.allowed());
        assertNotNull(result.semaphore());

        limiter.release(result);
    }

    @Test
    void shouldRejectWhenInputTooLarge() {
        limiter.registerLimits("ext-1", new ExtensionResourceLimits(
                4, 256, 50, 100, 100, 4 * 1024 * 1024, 30_000));

        ExtensionResourceLimiter.ExtensionCheckResult result = limiter.checkAndAcquire("ext-1", 200);
        assertFalse(result.allowed());
        assertEquals("INPUT_TOO_LARGE", result.rejectionCode());
        verify(auditPort).record(eq("system"), eq("RESOURCE_LIMIT_EXCEEDED"), eq("EXTENSION_RESOURCE"),
                eq("resource_limit"), eq("ext-1"), any(Map.class));
    }

    @Test
    void shouldRejectWhenConcurrencyLimitReached() {
        limiter.registerLimits("ext-1", new ExtensionResourceLimits(
                1, 256, 50, 100, 1024 * 1024, 4 * 1024 * 1024, 30_000));

        ExtensionResourceLimiter.ExtensionCheckResult first = limiter.checkAndAcquire("ext-1", 100);
        assertTrue(first.allowed());

        ExtensionResourceLimiter.ExtensionCheckResult second = limiter.checkAndAcquire("ext-1", 100);
        assertFalse(second.allowed());
        assertEquals("CONCURRENCY_LIMIT", second.rejectionCode());

        limiter.release(first);
    }

    @Test
    void shouldRejectWhenQueueFull() {
        limiter.registerLimits("ext-1", new ExtensionResourceLimits(
                1, 256, 50, 1, 1024 * 1024, 4 * 1024 * 1024, 30_000));

        ExtensionResourceLimiter.ExtensionCheckResult first = limiter.checkAndAcquire("ext-1", 100);
        assertTrue(first.allowed());

        ExtensionResourceLimiter.ExtensionCheckResult second = limiter.checkAndAcquire("ext-1", 100);
        assertFalse(second.allowed());
        assertEquals("QUEUE_FULL", second.rejectionCode());

        limiter.release(first);
    }

    @Test
    void shouldUpdateLimits() {
        limiter.registerLimits("ext-1", ExtensionResourceLimits.DEFAULTS);
        limiter.updateLimits("ext-1", ExtensionResourceLimits.FULLY_TRUSTED, "admin");

        ExtensionResourceLimits updated = limiter.getLimits("ext-1");
        assertEquals(ExtensionResourceLimits.FULLY_TRUSTED.maxConcurrency(), updated.maxConcurrency());
        verify(auditPort).record(eq("admin"), eq("RESOURCE_LIMIT_UPDATED"), eq("EXTENSION_RESOURCE"),
                eq("resource_limit"), eq("ext-1"), any(Map.class));
    }

    @Test
    void shouldRecordOutput() {
        limiter.registerLimits("ext-1", ExtensionResourceLimits.DEFAULTS);
        limiter.recordOutput("ext-1", 1024);

        Map<String, Object> stats = limiter.getUsageStats("ext-1");
        assertEquals(1024L, stats.get("totalOutputBytes"));
    }

    @Test
    void shouldGetUsageStats() {
        limiter.registerLimits("ext-1", ExtensionResourceLimits.DEFAULTS);

        Map<String, Object> stats = limiter.getUsageStats("ext-1");
        assertEquals(4, stats.get("maxConcurrency"));
        assertEquals(256, stats.get("maxMemoryMb"));
        assertEquals(50, stats.get("maxCpuPercent"));
        assertEquals(100, stats.get("maxQueueSize"));
        assertEquals(0L, stats.get("currentQueueSize"));
    }
}
