package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.billing.domain.CustomPricingRule;
import com.example.platform.billing.domain.DiscountPolicy;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingRuleServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private CommercialPricingJdbcRepository repository;
    private PricingRuleService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommercialPricingJdbcRepository.class);
        service = new PricingRuleService(repository);
        when(repository.saveRule(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveOverride(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveDiscount(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findEffectiveDiscounts(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void savesVersionedExactRuleAndListsTenantScopedGlobalRules() {
        PricingRule rule = rule("api-standard", "api", 5, List.of());
        when(repository.findRulesByTenant("GLOBAL")).thenReturn(List.of(rule));

        assertEquals(rule, service.saveRule(rule));
        assertEquals(List.of(rule), service.listPricingRules());
        verify(repository).findRulesByTenant("GLOBAL");
    }

    @Test
    void archiveUsesLegalActiveToArchivedTransition() {
        PricingRule rule = rule("api-standard", "api", 5, List.of());
        when(repository.findRule("GLOBAL", "api-standard", 1)).thenReturn(Optional.of(rule));

        assertEquals("ARCHIVED", service.archivePricingRule("api-standard").status());
        verify(repository).updateRuleStatus(org.mockito.ArgumentMatchers.eq("GLOBAL"),
                org.mockito.ArgumentMatchers.eq("api-standard"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("ACTIVE"),
                org.mockito.ArgumentMatchers.eq("ARCHIVED"), any());
    }

    @Test
    void unknownRuleFailsClosedWithoutInventingDefaultPrice() {
        when(repository.findEffectiveRule("tenant-a", "missing", 1, NOW))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "api", 10,
                        "missing", 1, NOW, Map.of())));
    }

    @Test
    void exactTenantOverrideAndRationalDiscountApplyWithoutFloatingPoint() {
        PricingRule rule = rule("api-standard", "api", 5, List.of());
        CustomPricingRule override = new CustomPricingRule("override-1", "tenant-a", null,
                "api", 1, new Money(4, "USD"), 1, 4,
                NOW.minusSeconds(1), null, "ACTIVE", NOW.minusSeconds(1));
        when(repository.findEffectiveRule("tenant-a", "api-standard", 1, NOW))
                .thenReturn(Optional.of(rule));
        when(repository.findOverride("tenant-a", null, "api", NOW))
                .thenReturn(Optional.of(override));

        var result = service.previewPricing(new PricingQuoteCommand(
                "tenant-a", null, "api", 3, "api-standard", 1, NOW, Map.of()));

        assertEquals(new Money(9, "USD"), result.amount());
        assertEquals("override-1", result.overrideRuleId());
    }

    @Test
    void exactPercentageAndFlatDiscountsPreserveBroadPricingBehavior() {
        PricingRule rule = rule("api-standard", "api", 10, List.of());
        when(repository.findEffectiveRule("tenant-a", "api-standard", 1, NOW))
                .thenReturn(Optional.of(rule));
        when(repository.findOverride("tenant-a", null, "api", NOW)).thenReturn(Optional.empty());
        DiscountPolicy percentage = new DiscountPolicy("d1", "tenant-a", "summer", 1,
                "api", "USD", "Summer", "", "PERCENTAGE", 1, 5, 0,
                Map.of(), "ACTIVE", NOW.minusSeconds(1), null, NOW);
        DiscountPolicy flat = new DiscountPolicy("d2", "tenant-a", "flat", 1,
                "api", "USD", "Flat", "", "FLAT", 0, 1, 100,
                Map.of(), "ACTIVE", NOW.minusSeconds(1), null, NOW);
        when(repository.findEffectiveDiscounts("tenant-a", "api", NOW))
                .thenReturn(List.of(percentage, flat));

        var result = service.previewPricing(new PricingQuoteCommand(
                "tenant-a", null, "api", 100, "api-standard", 1, NOW, Map.of()));

        assertEquals(new Money(700, "USD"), result.amount());
    }

    @Test
    void tieredPricingUsesIncreasingCumulativeThresholds() {
        PricingRule rule = rule("tiered", "api", 0,
                List.of(new PricingTier(100, 10, 0), new PricingTier(1_000, 5, 0)));
        when(repository.findEffectiveRule("tenant-a", "tiered", 1, NOW))
                .thenReturn(Optional.of(rule));
        when(repository.findOverride("tenant-a", null, "api", NOW)).thenReturn(Optional.empty());

        var result = service.previewPricing(new PricingQuoteCommand(
                "tenant-a", null, "api", 150, "tiered", 1, NOW, Map.of()));

        assertEquals(new Money(1_250, "USD"), result.amount());
    }

    @Test
    void overrideCurrencyMismatchFailsClosed() {
        PricingRule rule = rule("api-standard", "api", 5, List.of());
        when(repository.findEffectiveRule("tenant-a", "api-standard", 1, NOW))
                .thenReturn(Optional.of(rule));
        when(repository.findOverride("tenant-a", null, "api", NOW)).thenReturn(Optional.of(
                new CustomPricingRule("override-eur", "tenant-a", null, "api", 1,
                        new Money(4, "EUR"), 0, 1, NOW.minusSeconds(1), null, "ACTIVE", NOW)));

        assertThrows(IllegalStateException.class, () -> service.previewPricing(
                new PricingQuoteCommand("tenant-a", null, "api", 1,
                        "api-standard", 1, NOW, Map.of())));
    }

    private static PricingRule rule(String key, String meter, long price, List<PricingTier> tiers) {
        return new PricingRule("rule-" + key, "GLOBAL", key, 1, key, "",
                PricingModel.USAGE_BASED, meter, new Money(price, "USD"), tiers,
                "ACTIVE", NOW.minusSeconds(1), null, NOW.minusSeconds(1), NOW.minusSeconds(1));
    }
}
