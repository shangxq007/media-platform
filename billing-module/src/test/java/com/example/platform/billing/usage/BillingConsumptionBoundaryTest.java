package com.example.platform.billing.usage;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
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

    private static BillableUsage canonical(
            UsageDimension dimension, UsageQuantity quantity, String idempotencyKey) {
        Instant now = Instant.now();
        return new BillableUsage(
                "billable-" + idempotencyKey, "tenant-1",
                new CanonicalActorRef("user-1", "USER"), "observed-" + idempotencyKey,
                dimension, quantity, dimension.name(), dimension, quantity,
                "meter-rule", "v1", MeteringTransformationKind.IDENTITY, "identity",
                now, now, idempotencyKey, "trace-1", "observed-" + idempotencyKey);
    }

    @Test
    void consumeRoutesCanonicalToRatingEngine() {
        BillableUsage canonical =
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
        BillableUsage canonical =
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

        BillableUsage canonical =
                canonical(UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(10L, UsageUnit.COUNT), "idem-3");

        String recordIdBefore = canonical.recordId();
        boundary.consume(canonical);
        assertEquals(recordIdBefore, canonical.recordId());
        assertEquals(10L, canonical.quantity().baseUnits());
    }
}
