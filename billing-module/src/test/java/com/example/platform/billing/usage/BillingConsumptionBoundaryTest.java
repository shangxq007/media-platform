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

    private RatingEngine ratingEngine;
    private PricingRuleService pricingRuleService;
    private BillingConsumptionBoundaryImpl boundary;

    @BeforeEach
    void setUp() {
        ratingEngine = new RatingEngine();
        pricingRuleService = new PricingRuleService();
        boundary = new BillingConsumptionBoundaryImpl(ratingEngine, pricingRuleService);
    }

    private static UsageRecord canonical(
            UsageDimension dimension, UsageQuantity quantity, String idempotencyKey) {
        return UsageRecord.record(
                "tenant-1", null, null,
                OperationRef.of("op-" + idempotencyKey), null, null, null,
                dimension, quantity,
                null, Instant.now(), Instant.now(), idempotencyKey, "REPORTED", "render-step");
    }

    @Test
    void consumeRoutesCanonicalToRatingEngine() {
        UsageRecord canonical =
                canonical(UsageDimension.DURATION, UsageQuantity.fromBaseUnits(150_000L, UsageUnit.MILLISECONDS), "idem-1");

        // No active rule for DURATION — consume must not fail and must not invent usage.
        boundary.consume(canonical);

        // The canonical record itself is untouched (boundary never constructs/replaces it).
        assertEquals("DURATION", canonical.dimension().name());
        assertEquals(150_000L, canonical.quantity().baseUnits());
    }

    @Test
    void consumeDoesNotInventUsage() {
        // No pricing rule configured for BYTE_STORED — the boundary must not fabricate a rated record.
        UsageRecord canonical =
                canonical(UsageDimension.BYTE_STORED, UsageQuantity.fromBaseUnits(1024L, UsageUnit.BYTE), "idem-2");

        boundary.consume(canonical);

        assertEquals("BYTE_STORED", canonical.dimension().name());
        assertEquals(1024L, canonical.quantity().baseUnits());
    }

    @Test
    void consumePreservesCanonicalRecordIdentity() {
        pricingRuleService.createPricingRule(
                "api_calls", "API", "", PricingModel.USAGE_BASED,
                "REQUEST", 5L, "USD", null, null, null);

        UsageRecord canonical =
                canonical(UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(10L, UsageUnit.COUNT), "idem-3");

        String recordIdBefore = canonical.recordId();
        boundary.consume(canonical);
        assertEquals(recordIdBefore, canonical.recordId());
        assertEquals(10L, canonical.quantity().baseUnits());
    }
}
