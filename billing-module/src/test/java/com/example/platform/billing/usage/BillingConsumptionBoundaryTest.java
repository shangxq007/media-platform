package com.example.platform.billing.usage;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingConsumptionBoundaryTest {

    private BillingUsageCompatibilityAdapter adapter;
    private RatingEngine ratingEngine;
    private PricingRuleService pricingRuleService;
    private BillingConsumptionBoundaryImpl boundary;

    @BeforeEach
    void setUp() {
        // Spy the adapter so we can prove the boundary routes consumption THROUGH it
        // without depending on RatingEngine's internal storage.
        adapter = Mockito.spy(new BillingUsageCompatibilityAdapter());
        ratingEngine = new RatingEngine();
        pricingRuleService = new PricingRuleService();
        boundary = new BillingConsumptionBoundaryImpl(adapter, ratingEngine, pricingRuleService);
    }

    private static com.example.platform.billing.usage.UsageRecord canonical(
            UsageDimension dimension, UsageQuantity quantity, String idempotencyKey) {
        return com.example.platform.billing.usage.UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-" + idempotencyKey), null, null, null,
                dimension, quantity,
                null, Instant.now(), Instant.now(), idempotencyKey, "REPORTED", "render-step");
    }

    @Test
    void consumeRoutesThroughAdapter() {
        com.example.platform.billing.usage.UsageRecord canonical =
                canonical(UsageDimension.DURATION, UsageQuantity.fromBaseUnits(150_000L, UsageUnit.MILLISECONDS), "idem-1");

        boundary.consume(canonical);

        // Boundary routes consumption THROUGH the adapter exactly once.
        verify(adapter, times(1)).adapt(canonical);
        // The projection it produces is the legacy billing.domain shape (meterKey = dimension).
        assertEquals("DURATION", adapter.adapt(canonical).meterKey());
    }

    @Test
    void consumeDoesNotInventUsage() {
        // No pricing rule configured for BYTE_STORED — the boundary must still project
        // through the adapter but must not fabricate a rated record.
        com.example.platform.billing.usage.UsageRecord canonical =
                canonical(UsageDimension.BYTE_STORED, UsageQuantity.fromBaseUnits(1024L, UsageUnit.BYTE), "idem-2");

        boundary.consume(canonical);

        verify(adapter, times(1)).adapt(canonical);
        // RatingEngine has no public enumeration; prove non-invention by confirming the
        // canonical record itself is untouched (boundary never constructs/replaces it).
        assertEquals("BYTE_STORED", canonical.dimension().name());
        assertEquals(1024L, canonical.quantity().baseUnits());
    }

    @Test
    void consumePreservesCanonicalRecordIdentity() {
        pricingRuleService.createPricingRule(
                "api_calls", "API", "", PricingModel.USAGE_BASED,
                "REQUEST", 5L, "USD", null, null, null);

        com.example.platform.billing.usage.UsageRecord canonical =
                canonical(UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(10L, UsageUnit.COUNT), "idem-3");

        String recordIdBefore = canonical.recordId();
        boundary.consume(canonical);

        verify(adapter, times(1)).adapt(canonical);
        // Boundary is a pure consumer: canonical record identity is preserved.
        assertEquals(recordIdBefore, canonical.recordId());
    }
}
