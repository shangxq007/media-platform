package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.SubscriptionContractRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BillingCycleServiceTest {

    private BillingCycleService cycleService;
    private SubscriptionBillingService subscriptionBillingService;
    private UsageMeteringService usageMeteringService;
    private PricingRuleService pricingRuleService;

    @BeforeEach
    void setUp() {
        subscriptionBillingService = new SubscriptionBillingService();
        usageMeteringService = new UsageMeteringService();
        pricingRuleService = new PricingRuleService();
        cycleService = new BillingCycleService(
                usageMeteringService,
                subscriptionBillingService,
                pricingRuleService,
                new BillingLedgerService(),
                new CreditWalletService());

        pricingRuleService.createPricingRule(
                "render_minutes_overage", "Render", "", PricingModel.USAGE_BASED,
                "render.minutes", 10L, "USD", null, null, null);
        subscriptionBillingService.createPlan(
                "pro_monthly", "Pro", "", "MONTHLY", 9999, "USD",
                Map.of("render.minutes", 100L));
        subscriptionBillingService.createSubscription(
                "t1", "u1", "pro_monthly", "pro_monthly", 30, SubscriptionContractRole.BASE);
    }

    @Test
    void chargesOverageBeyondIncludedQuota() {
        usageMeteringService.recordUsage(
                "t1", null, "u1", "render.minutes", 150, "minute", null, null);

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(500L, result.totalChargeMinor());
        assertTrue(result.lines().stream().anyMatch(l -> "OVERAGE".equals(l.disposition())));
    }

    @Test
    void noChargeWhenWithinIncludedQuota() {
        usageMeteringService.recordUsage(
                "t1", null, "u1", "render.minutes", 50, "minute", null, null);

        BillingCycleService.BillingCycleResult result = cycleService.runCycle("t1", "u1");

        assertEquals(0L, result.totalChargeMinor());
    }
}
