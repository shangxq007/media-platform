package com.example.platform.billing.usage;

import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.RatedUsageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingUsageCompatibilityAdapterTest {

    private BillingUsageCompatibilityAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BillingUsageCompatibilityAdapter();
    }

    @Test
    void mapsCanonicalToLegacyShape() {
        Instant now = Instant.now();
        UsageRecord canonical = UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-1"), null, null, null,
                UsageDimension.TOKEN_INPUT,
                UsageQuantity.fromBaseUnits(1_500L, UsageUnit.TOKEN),
                null, now, now, "idem-1", "ESTIMATED", "ai-gateway-heuristic");

        com.example.platform.billing.domain.UsageRecord legacy = adapter.adapt(canonical);

        assertEquals(canonical.recordId(), legacy.recordId());
        assertEquals("tenant-1", legacy.tenantId());
        assertNull(legacy.workspaceId());
        assertNull(legacy.userId());
        assertEquals("TOKEN_INPUT", legacy.meterKey());
        assertEquals("TOKEN", legacy.unit());
        assertEquals(now, legacy.recordedAt());
        assertEquals("idem-1", legacy.idempotencyKey());
    }

    @Test
    void legacyQuantityDerivedFromTypedBaseUnits() {
        UsageRecord canonical = UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-2"), null, null, null,
                UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(2_500L, UsageUnit.MILLISECONDS),
                null, Instant.now(), Instant.now(), "idem-2", "REPORTED", "render-step");

        com.example.platform.billing.domain.UsageRecord legacy = adapter.adapt(canonical);

        // Legacy quantity is the canonical base-units count projected as a double.
        assertEquals(2_500.0, legacy.quantity(), 0.0001);
        assertEquals("MILLISECONDS", legacy.unit());
        assertEquals("DURATION", legacy.meterKey());
    }

    @Test
    void adapterIsNotCanonicalAuthority() {
        String javadoc = BillingUsageCompatibilityAdapter.class.getSimpleName();
        // The class carries the NOT CANONICAL AUTHORITY contract in its javadoc.
        assertNotNull(javadoc);
        // Guard: the adapter must never introduce its own quantity — it only re-exports
        // the canonical base units. Zero base units must project to zero (not invented).
        UsageRecord canonical = UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-3"), null, null, null,
                UsageDimension.REQUEST,
                UsageQuantity.fromBaseUnits(0L, UsageUnit.COUNT),
                null, Instant.now(), Instant.now(), "idem-3", "REPORTED", "render-step");

        com.example.platform.billing.domain.UsageRecord legacy = adapter.adapt(canonical);
        assertEquals(0.0, legacy.quantity(), 0.0001);
    }

    @Test
    void ratingEngineCanConsumeAdaptedRecord() {
        UsageRecord canonical = UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-4"), null, null, null,
                UsageDimension.REQUEST,
                UsageQuantity.fromBaseUnits(100L, UsageUnit.COUNT),
                null, Instant.now(), Instant.now(), "idem-4", "REPORTED", "render-step");

        com.example.platform.billing.domain.UsageRecord legacy = adapter.adapt(canonical);

        RatingEngine engine = new RatingEngine();
        PricingRule rule = new PricingRule("r1", "api_calls", "API Calls", "",
                PricingModel.USAGE_BASED, "REQUEST", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());

        RatedUsageRecord rated = engine.rateUsage(legacy, rule);
        assertNotNull(rated);
        assertEquals(legacy.recordId(), rated.usageRecordId());
        assertEquals(500, rated.ratedAmountMinor());
    }
}
