package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
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

    @Test
    void shouldRateUsageWithFlatPrice() {
        Instant now = Instant.now();
        UsageRecord usage = new UsageRecord("u1", "t1", "ws-1", "user-1",
                "api_calls", 100.0, "calls", now, null);
        PricingRule rule = new PricingRule("r1", "rule-key", "API Calls", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, now, now);

        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated);
        assertEquals("u1", rated.usageRecordId());
        assertEquals("r1", rated.pricingRuleId());
        assertEquals(500, rated.ratedAmountMinor());
        assertEquals("USD", rated.currencyCode());
    }

    @Test
    void shouldRateUsageWithTiers() {
        Instant now = Instant.now();
        UsageRecord usage = new UsageRecord("u1", "t1", "ws-1", "user-1",
                "api_calls", 150.0, "calls", now, null);
        List<PricingTier> tiers = List.of(
                new PricingTier(100, 5, 0),
                new PricingTier(1000, 3, 0));
        PricingRule rule = new PricingRule("r1", "rule-key", "Tiered API", "",
                PricingModel.USAGE_BASED, "api_calls", 0, "USD",
                tiers, "ACTIVE", null, null, now, now);

        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated);
        assertEquals(650, rated.ratedAmountMinor());
    }

    @Test
    void shouldThrowOnNullUsageRecord() {
        Instant now = Instant.now();
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, now, now);
        assertThrows(IllegalArgumentException.class, () -> engine.rateUsage(null, rule));
    }

    @Test
    void shouldThrowOnNullPricingRule() {
        Instant now = Instant.now();
        UsageRecord usage = new UsageRecord("u1", "t1", "ws-1", "user-1",
                "api_calls", 100.0, "calls", now, null);
        assertThrows(IllegalArgumentException.class, () -> engine.rateUsage(usage, null));
    }

    @Test
    void shouldGetRatedRecord() {
        Instant now = Instant.now();
        UsageRecord usage = new UsageRecord("u1", "t1", "ws-1", "user-1",
                "api_calls", 10.0, "calls", now, null);
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, now, now);
        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        RatedUsageRecord found = engine.getRatedRecord(rated.ratedUsageId());
        assertNotNull(found);
        assertEquals(rated.ratedUsageId(), found.ratedUsageId());
    }

    @Test
    void shouldIncludeRatingDetails() {
        Instant now = Instant.now();
        UsageRecord usage = new UsageRecord("u1", "t1", "ws-1", "user-1",
                "api_calls", 10.0, "calls", now, null);
        PricingRule rule = new PricingRule("r1", "rule-key", "", "",
                PricingModel.USAGE_BASED, "api_calls", 5, "USD",
                List.of(), "ACTIVE", null, null, now, now);
        RatedUsageRecord rated = engine.rateUsage(usage, rule);
        assertNotNull(rated.ratingDetails());
        assertEquals("api_calls", rated.ratingDetails().get("meterKey"));
        assertEquals(10.0, rated.ratingDetails().get("quantity"));
    }
}
