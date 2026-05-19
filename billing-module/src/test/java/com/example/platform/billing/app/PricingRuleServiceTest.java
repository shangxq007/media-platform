package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PricingRuleServiceTest {

    private PricingRuleService service;

    @BeforeEach
    void setUp() {
        service = new PricingRuleService();
    }

    @Test
    void shouldCreatePricingRule() {
        PricingRule rule = service.createPricingRule(
                "api-standard", "API Standard", "",
                PricingModel.USAGE_BASED, "api_calls",
                5, "USD", List.of(), null, null);
        assertNotNull(rule);
        assertEquals("api-standard", rule.ruleKey());
        assertEquals(5, rule.unitPriceMinor());
        assertEquals("USD", rule.currencyCode());
        assertEquals("ACTIVE", rule.status());
    }

    @Test
    void shouldGetPricingRule() {
        service.createPricingRule("r1", "Rule 1", "", PricingModel.USAGE_BASED, "m1", 10, "USD", List.of(), null, null);
        PricingRule rule = service.getPricingRule("r1");
        assertNotNull(rule);
        assertEquals("r1", rule.ruleKey());
    }

    @Test
    void shouldListPricingRules() {
        service.createPricingRule("r1", "Rule 1", "", PricingModel.USAGE_BASED, "m1", 10, "USD", List.of(), null, null);
        service.createPricingRule("r2", "Rule 2", "", PricingModel.SUBSCRIPTION, "m2", 100, "USD", List.of(), null, null);
        List<PricingRule> rules = service.listPricingRules();
        assertEquals(2, rules.size());
    }

    @Test
    void shouldArchivePricingRule() {
        service.createPricingRule("r1", "Rule 1", "", PricingModel.USAGE_BASED, "m1", 10, "USD", List.of(), null, null);
        PricingRule archived = service.archivePricingRule("r1");
        assertEquals("ARCHIVED", archived.status());
    }

    @Test
    void shouldThrowOnArchiveUnknownRule() {
        assertThrows(IllegalArgumentException.class, () -> service.archivePricingRule("nonexistent"));
    }

    @Test
    void shouldCreateCustomPricing() {
        CustomPricingRule rule = service.createCustomPricing(
                "t1", "ws-1", "api_calls", 3L, 10.0,
                Instant.now(), null);
        assertNotNull(rule);
        assertEquals("t1", rule.tenantId());
        assertEquals("api_calls", rule.meterKey());
        assertEquals(3L, rule.overridePriceMinor());
        assertEquals(10.0, rule.discountPercent());
    }

    @Test
    void shouldGetCustomPricing() {
        CustomPricingRule rule = service.createCustomPricing(
                "t1", "ws-1", "api_calls", null, null, null, null);
        CustomPricingRule found = service.getCustomPricing(rule.ruleId());
        assertNotNull(found);
        assertEquals(rule.ruleId(), found.ruleId());
    }

    @Test
    void shouldGetCustomPricingForTenant() {
        service.createCustomPricing("t1", "ws-1", "api_calls", null, null, null, null);
        service.createCustomPricing("t1", "ws-2", "storage", null, null, null, null);
        service.createCustomPricing("t2", "ws-1", "api_calls", null, null, null, null);
        List<CustomPricingRule> t1Rules = service.getCustomPricingForTenant("t1");
        assertEquals(2, t1Rules.size());
    }

    @Test
    void shouldCreateDiscountPolicy() {
        DiscountPolicy policy = service.createDiscountPolicy(
                "summer-2025", "Summer Sale", "20% off",
                "PERCENTAGE", 20.0,
                Map.of("region", "US"),
                Instant.now(), null);
        assertNotNull(policy);
        assertEquals("summer-2025", policy.policyKey());
        assertEquals("PERCENTAGE", policy.discountType());
        assertEquals(20.0, policy.discountValue());
    }

    @Test
    void shouldListActiveDiscountPolicies() {
        service.createDiscountPolicy("d1", "Discount 1", "", "PERCENTAGE", 10.0, Map.of(), null, null);
        service.createDiscountPolicy("d2", "Discount 2", "", "FLAT", 50.0, Map.of(), null, null);
        List<DiscountPolicy> policies = service.listDiscountPolicies();
        assertEquals(2, policies.size());
    }

    @Test
    void shouldPreviewPricing() {
        service.createPricingRule("api-standard", "API", "",
                PricingModel.USAGE_BASED, "api_calls",
                5, "USD", List.of(), null, null);
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "api_calls", 100.0, Map.of());
        assertNotNull(result);
        assertEquals(500, result.estimatedAmountMinor());
        assertEquals("USD", result.currencyCode());
    }

    @Test
    void shouldPreviewWithCustomPricing() {
        service.createPricingRule("api-standard", "API", "",
                PricingModel.USAGE_BASED, "api_calls",
                5, "USD", List.of(), null, null);
        service.createCustomPricing("t1", "ws-1", "api_calls", 3L, null, Instant.now(), null);
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "api_calls", 100.0, Map.of());
        assertEquals(300, result.estimatedAmountMinor());
    }

    @Test
    void shouldPreviewWithDiscount() {
        service.createPricingRule("api-standard", "API", "",
                PricingModel.USAGE_BASED, "api_calls",
                10, "USD", List.of(), null, null);
        service.createDiscountPolicy("summer", "Sale", "",
                "PERCENTAGE", 20.0, Map.of(), Instant.now(), null);
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "api_calls", 100.0, Map.of());
        assertEquals(800, result.estimatedAmountMinor());
    }

    @Test
    void shouldPreviewWithDefaultPricingWhenNoRule() {
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "unknown_meter", 10.0, Map.of());
        assertNotNull(result);
        assertEquals(1000, result.estimatedAmountMinor());
    }

    @Test
    void shouldPreviewWithFlatDiscount() {
        service.createPricingRule("api-standard", "API", "",
                PricingModel.USAGE_BASED, "api_calls",
                10, "USD", List.of(), null, null);
        service.createDiscountPolicy("flat-discount", "Flat", "",
                "FLAT", 200.0, Map.of(), Instant.now(), null);
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "api_calls", 100.0, Map.of());
        assertEquals(800, result.estimatedAmountMinor());
    }

    @Test
    void shouldPreviewWithTieredPricing() {
        List<PricingTier> tiers = List.of(
                new PricingTier(100, 10, 0),
                new PricingTier(1000, 5, 0));
        service.createPricingRule("api-tiered", "Tiered", "",
                PricingModel.USAGE_BASED, "api_calls",
                0, "USD", tiers, null, null);
        PricingRuleService.PricingPreviewResult result = service.previewPricing(
                "t1", "api_calls", 150.0, Map.of());
        assertEquals(1250, result.estimatedAmountMinor());
    }
}
