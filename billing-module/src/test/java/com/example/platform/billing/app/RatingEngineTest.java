package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.domain.RateUsageCommand;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RatingEngineTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private CommercialPricingJdbcRepository pricing;
    private RatedUsageJdbcRepository records;
    private RatedUsageAuditPort audit;
    private RatingEngine engine;

    @BeforeEach
    void setUp() {
        pricing = mock(CommercialPricingJdbcRepository.class);
        records = mock(RatedUsageJdbcRepository.class);
        audit = mock(RatedUsageAuditPort.class);
        engine = new RatingEngine(pricing, records, audit);
        when(records.append(any())).thenAnswer(invocation ->
                new RatedUsageJdbcRepository.AppendResult(invocation.getArgument(0), true));
    }

    @Test
    void ratesBillableUsageWithExactMoneyAndAuditsInsert() {
        PricingRule rule = rule(5, List.of());
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.of(rule));

        var rated = engine.rate(command(usage("bill-1", 100), "idem-1"));

        assertEquals(new Money(500, "USD"), rated.amount());
        assertEquals(100L, rated.quantityBaseUnits());
        verify(audit).record(rated);
    }

    @Test
    void tieredRatingPreservesExactThresholdBehavior() {
        PricingRule rule = rule(0,
                List.of(new PricingTier(100, 5, 0), new PricingTier(1_000, 3, 0)));
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.of(rule));

        assertEquals(new Money(650, "USD"),
                engine.rate(command(usage("bill-tier", 150), "idem-tier")).amount());
    }

    @Test
    void unknownRuleAndMeterMismatchFailClosed() {
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> engine.rate(command(usage("missing", 1), "idem-missing")));

        PricingRule wrongMeter = new PricingRule("wrong", "GLOBAL", "api-rule", 1,
                "wrong", "", PricingModel.USAGE_BASED, "other",
                new Money(1, "USD"), List.of(), "ACTIVE", NOW.minusSeconds(1), null, NOW, NOW);
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.of(wrongMeter));
        assertThrows(IllegalStateException.class,
                () -> engine.rate(command(usage("wrong", 1), "idem-wrong")));
    }

    @Test
    void replayDoesNotDuplicateAudit() {
        PricingRule rule = rule(5, List.of());
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.of(rule));
        when(records.append(any())).thenAnswer(invocation ->
                new RatedUsageJdbcRepository.AppendResult(invocation.getArgument(0), false));

        engine.rate(command(usage("replay", 1), "idem-replay"));

        org.mockito.Mockito.verifyNoInteractions(audit);
    }

    @Test
    void multiplicationOverflowRejectsRating() {
        when(pricing.findEffectiveRule("tenant-a", "api-rule", 1, NOW))
                .thenReturn(Optional.of(rule(Long.MAX_VALUE, List.of())));
        assertThrows(ArithmeticException.class,
                () -> engine.rate(command(usage("overflow", 2), "idem-overflow")));
    }

    private static RateUsageCommand command(BillableUsage usage, String key) {
        return new RateUsageCommand(usage, "api-rule", 1, key, NOW, "trace-rate");
    }

    private static BillableUsage usage(String id, long quantity) {
        UsageQuantity exact = new UsageQuantity(quantity, UsageUnit.COUNT);
        return new BillableUsage(id, "tenant-a", new CanonicalActorRef("user", "USER"),
                "observed-" + id, UsageDimension.REQUEST, exact, "api",
                UsageDimension.REQUEST, exact, "meter", "v1",
                MeteringTransformationKind.IDENTITY, "identity", NOW, NOW,
                "bill-" + id, "trace-bill", "observed-" + id);
    }

    private static PricingRule rule(long price, List<PricingTier> tiers) {
        return new PricingRule("rule-api", "GLOBAL", "api-rule", 1, "API", "",
                PricingModel.USAGE_BASED, "api", new Money(price, "USD"), tiers,
                "ACTIVE", NOW.minusSeconds(1), null, NOW, NOW);
    }
}
