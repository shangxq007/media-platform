package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageUnit;
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

    private static UsageRecord canonicalUsage(String recordId, long baseUnits) {
        return new UsageRecord(
                recordId, "t1", null, null, null, null, null, null,
                UsageDimension.REQUEST, new UsageQuantity(baseUnits, UsageUnit.COUNT),
                Instant.now(), Instant.now(), Instant.now(), null, "REPORTED", "test");
    }

    @Test
    void shouldRateUsageWithFlatPrice() {
        UsageRecord usage = canonicalUsage("u1", 100L);
        PricingRule rule = new PricingRule("r1", "rule-key", "API Calls", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());

        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated);
        assertEquals("u1", rated.usageRecordId());
        assertEquals("r1", rated.pricingRuleId());
        assertEquals(500, rated.ratedAmountMinor());
        assertEquals("USD", rated.currencyCode());
    }

    @Test
    void shouldRateUsageWithTiers() {
        UsageRecord usage = canonicalUsage("u1", 150L);
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
        UsageRecord usage = canonicalUsage("u1", 100L);
        assertThrows(IllegalArgumentException.class, () -> engine.rateUsage(usage, null));
    }

    @Test
    void shouldGetRatedRecord() {
        UsageRecord usage = canonicalUsage("u1", 10L);
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
        UsageRecord usage = canonicalUsage("u1", 10L);
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, Instant.now(), Instant.now());
        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated.ratingDetails());
        assertEquals("REQUEST", rated.ratingDetails().get("meterKey"));
        assertEquals(10.0, rated.ratingDetails().get("quantity"));
    }
}
