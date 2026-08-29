package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RatingEngineTest {

    private RatingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RatingEngine();
    }

    private static BillableUsage canonicalUsage(String recordId, long baseUnits) {
        Instant now = Instant.now();
        UsageQuantity quantity = new UsageQuantity(baseUnits, UsageUnit.COUNT);
        return new BillableUsage(
                recordId, "t1", new CanonicalActorRef("user-1", "USER"), "observed-1",
                UsageDimension.REQUEST, quantity, "api_calls", UsageDimension.REQUEST,
                quantity, "meter-rule", "v1", MeteringTransformationKind.IDENTITY,
                "identity", now, now, "idem-" + recordId, "trace-1", "observed-1");
    }

    @Test
    void shouldRateUsageWithFlatPrice() {
        BillableUsage usage = canonicalUsage("u1", 100L);
        PricingRule rule = new PricingRule("r1", "rule-key", "API Calls", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());

        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated);
        assertEquals("u1", rated.billableUsageId());
        assertEquals("r1", rated.pricingRuleId());
        assertEquals(500, rated.ratedAmountMinor());
        assertEquals("USD", rated.currencyCode());
    }

    @Test
    void shouldRateUsageWithTiers() {
        BillableUsage usage = canonicalUsage("u1", 150L);
        List<PricingTier> tiers = List.of(
                new PricingTier(100, 5, 0),
                new PricingTier(1000, 3, 0));
        PricingRule rule = new PricingRule("r1", "rule-key", "Tiered API", "",
                PricingModel.USAGE_BASED, "api_calls", 0, "USD",
                tiers, "ACTIVE", null, null, Instant.now(), Instant.now());

        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated);
        assertEquals(650, rated.ratedAmountMinor());
    }

    @Test
    void shouldThrowOnNullUsageRecord() {
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> engine.rateUsage(null, rule));
    }

    @Test
    void shouldThrowOnNullPricingRule() {
        BillableUsage usage = canonicalUsage("u1", 100L);
        assertThrows(IllegalArgumentException.class, () -> engine.rateUsage(usage, null));
    }

    @Test
    void shouldGetRatedRecord() {
        BillableUsage usage = canonicalUsage("u1", 10L);
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());
        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        RatedUsageRecord found = engine.getRatedRecord(rated.ratedUsageId());
        assertNotNull(found);
        assertEquals(rated.ratedUsageId(), found.ratedUsageId());
    }

    @Test
    void shouldIncludeRatingDetails() {
        BillableUsage usage = canonicalUsage("u1", 10L);
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());
        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated.ratingDetails());
        assertEquals("api_calls", rated.ratingDetails().get("meterKey"));
        assertEquals(10L, rated.ratingDetails().get("quantity"));
    }
}
