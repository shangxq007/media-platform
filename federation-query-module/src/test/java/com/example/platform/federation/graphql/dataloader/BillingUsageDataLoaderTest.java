package com.example.platform.federation.graphql.dataloader;

import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingUsageDataLoaderTest {

    private static final Instant NOW = Instant.now();

    /** Canonical usage fact with an explicit recordId (direct record ctor; factory generates ids). */
    private static UsageRecord canonicalUsage(String recordId, String tenantId, String meterKey,
                                              long quantity, String unit) {
        UsageUnit canonicalUnit = switch (unit) {
            case "min", "seconds", "s" -> UsageUnit.SECONDS;
            case "bytes", "byte" -> UsageUnit.BYTE;
            default -> UsageUnit.COUNT;
        };
        UsageDimension dimension = switch (canonicalUnit) {
            case SECONDS, MILLISECONDS -> UsageDimension.DURATION;
            case BYTE -> UsageDimension.BYTE_STORED;
            default -> UsageDimension.REQUEST;
        };
        return new UsageRecord(
                recordId, tenantId, null, null, null, null, null, null,
                dimension, new UsageQuantity(quantity, canonicalUnit),
                NOW, NOW, NOW, null, "REPORTED", "test");
    }

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void loadsUsageDataWithExplicitTenantId() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        UsageRecord record = canonicalUsage("rec-1", "tenant-1", "render_minutes", 10, "min");
        when(meteringService.getUsageByTenant("tenant-1")).thenReturn(List.of(record));

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        CompletionStage<Map<String, List<Map<String, Object>>>> stage = loader.load(Set.of("tenant-1"));
        Map<String, List<Map<String, Object>>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("tenant-1"));
        assertEquals(1, result.get("tenant-1").size());
        assertEquals("rec-1", result.get("tenant-1").get(0).get("id"));
        assertEquals("DURATION", result.get("tenant-1").get(0).get("meterKey"));
        assertEquals(10L, result.get("tenant-1").get(0).get("quantity"));
    }

    @Test
    void doesNotTouchTenantContext() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        when(meteringService.getUsageByTenant("tenant-1")).thenReturn(List.of());

        // Set a known tenant context before calling
        TenantContext.set("original-tenant");

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        loader.load(Set.of("tenant-1")).toCompletableFuture().get();

        // TenantContext should be unchanged — loader does not manipulate it
        assertEquals("original-tenant", TenantContext.get());
    }

    @Test
    void doesNotLeakTenantContextBetweenTenants() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        UsageRecord record1 = canonicalUsage("rec-1", "tenant-1", "render_minutes", 10, "min");
        UsageRecord record2 = canonicalUsage("rec-2", "tenant-2", "render_minutes", 20, "min");
        when(meteringService.getUsageByTenant("tenant-1")).thenReturn(List.of(record1));
        when(meteringService.getUsageByTenant("tenant-2")).thenReturn(List.of(record2));

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        Map<String, List<Map<String, Object>>> result = loader.load(Set.of("tenant-1", "tenant-2"))
                .toCompletableFuture().get();

        // Each tenant gets their own data
        assertEquals(1, result.get("tenant-1").size());
        assertEquals(1, result.get("tenant-2").size());
        assertEquals("rec-1", result.get("tenant-1").get(0).get("id"));
        assertEquals("rec-2", result.get("tenant-2").get(0).get("id"));
    }

    @Test
    void tenantContextNotPollutedAfterLoaderCompletes() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        when(meteringService.getUsageByTenant(anyString())).thenReturn(List.of());

        // Simulate running on a thread pool thread that previously had a different tenant
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Set tenant on the pool thread
            executor.submit(() -> TenantContext.set("stale-tenant")).get();

            // Run loader on the same pool thread
            BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
            executor.submit(() -> {
                try {
                    loader.load(Set.of("tenant-1")).toCompletableFuture().get();
                } catch (Exception e) {
                    fail("Loader failed: " + e.getMessage());
                }
            }).get();

            // Verify TenantContext was not modified by the loader
            String remaining = executor.submit(() -> TenantContext.get()).get();
            assertEquals("stale-tenant", remaining,
                    "Loader should not modify TenantContext on async threads");
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void handlesMultipleTenantsConcurrently() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        for (int i = 0; i < 10; i++) {
            String tenantId = "tenant-" + i;
            UsageRecord record = canonicalUsage("rec-" + i, tenantId, "meter", i, "calls");
            when(meteringService.getUsageByTenant(tenantId)).thenReturn(List.of(record));
        }

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        Set<String> keys = Set.of(
                "tenant-0", "tenant-1", "tenant-2", "tenant-3", "tenant-4",
                "tenant-5", "tenant-6", "tenant-7", "tenant-8", "tenant-9"
        );
        Map<String, List<Map<String, Object>>> result = loader.load(keys).toCompletableFuture().get();

        assertEquals(10, result.size());
        for (int i = 0; i < 10; i++) {
            assertTrue(result.containsKey("tenant-" + i));
            assertEquals(1, result.get("tenant-" + i).size());
            assertEquals("rec-" + i, result.get("tenant-" + i).get(0).get("id"));
        }
    }

    @Test
    void failedTenantDoesNotAffectOthers() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        when(meteringService.getUsageByTenant("tenant-good")).thenReturn(List.of(
                canonicalUsage("rec-1", "tenant-good", "meter", 1, "calls")
        ));
        when(meteringService.getUsageByTenant("tenant-bad")).thenThrow(new RuntimeException("DB error"));

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        Map<String, List<Map<String, Object>>> result = loader.load(Set.of("tenant-good", "tenant-bad"))
                .toCompletableFuture().get();

        // Good tenant still gets data
        assertEquals(1, result.get("tenant-good").size());
        // Bad tenant gets empty list (graceful degradation)
        assertTrue(result.get("tenant-bad").isEmpty());
    }
}
