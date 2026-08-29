package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BillingConsumptionBoundaryTest {

    private RatingEngine rating;
    private PricingRuleService pricing;
    private BillingConsumptionBoundaryImpl boundary;

    @BeforeEach
    void setUp() {
        rating = mock(RatingEngine.class);
        pricing = mock(PricingRuleService.class);
        boundary = new BillingConsumptionBoundaryImpl(rating, pricing);
    }

    @Test
    void consumeRoutesOnlyCanonicalBillableUsageWithExplicitRuleVersion() {
        BillableUsage usage = canonical(UsageDimension.REQUEST,
                UsageQuantity.fromBaseUnits(10, UsageUnit.COUNT), "idem");
        PricingRule rule = new PricingRule("rule-api", "GLOBAL", "api-rule", 7,
                "API", "", PricingModel.USAGE_BASED, "REQUEST", new Money(5, "USD"),
                List.of(), "ACTIVE", Instant.EPOCH, null, Instant.EPOCH, Instant.EPOCH);
        when(pricing.requireEffectiveRuleForMeter(
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("REQUEST"), any())).thenReturn(rule);

        boundary.consume(usage);

        verify(rating).rate(org.mockito.ArgumentMatchers.argThat(command ->
                command.billableUsage() == usage && command.pricingRuleVersion() == 7));
        assertEquals("billable-idem", usage.billableUsageId());
    }

    @Test
    void unknownMeterFailsClosedAndNeverInventsDefaultPricing() {
        BillableUsage usage = canonical(UsageDimension.BYTE_STORED,
                UsageQuantity.fromBaseUnits(1_024, UsageUnit.BYTE), "unknown");
        when(pricing.requireEffectiveRuleForMeter(any(), any(), any()))
                .thenThrow(new IllegalStateException("unknown meter"));

        assertThrows(IllegalStateException.class, () -> boundary.consume(usage));
        org.mockito.Mockito.verifyNoInteractions(rating);
    }

    private static BillableUsage canonical(
            UsageDimension dimension, UsageQuantity quantity, String key) {
        Instant now = Instant.now();
        return new BillableUsage("billable-" + key, "tenant-1",
                new CanonicalActorRef("user-1", "USER"), "observed-" + key,
                dimension, quantity, dimension.name(), dimension, quantity,
                "meter-rule", "v1", MeteringTransformationKind.IDENTITY, "identity",
                now, now, key, "trace-1", "observed-" + key);
    }
}
