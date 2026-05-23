package com.example.platform.billing.app;

import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.domain.SubscriptionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionBillingServiceTest {

    private SubscriptionBillingService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionBillingService();
    }

    @Test
    void shouldCreatePlan() {
        SubscriptionPlan plan = service.createPlan(
                "pro_monthly", "Pro Monthly", "Professional tier",
                "MONTHLY", 2999, "USD",
                Map.of("render_seconds", 10000L, "api_calls", 50000L));
        assertNotNull(plan);
        assertEquals("pro_monthly", plan.planKey());
        assertEquals(2999, plan.basePriceMinor());
        assertEquals("USD", plan.currencyCode());
        assertEquals("ACTIVE", plan.status());
    }

    @Test
    void shouldGetPlan() {
        service.createPlan("pro_monthly", "Pro Monthly", "", "MONTHLY", 2999, "USD", Map.of());
        SubscriptionPlan plan = service.getPlan("pro_monthly");
        assertNotNull(plan);
        assertEquals("pro_monthly", plan.planKey());
    }

    @Test
    void shouldListActivePlans() {
        service.createPlan("p1", "Plan 1", "", "MONTHLY", 999, "USD", Map.of());
        service.createPlan("p2", "Plan 2", "", "MONTHLY", 2999, "USD", Map.of());
        List<SubscriptionPlan> plans = service.listPlans();
        assertEquals(2, plans.size());
    }

    @Test
    void shouldCreateSubscription() {
        service.createPlan("pro_monthly", "Pro Monthly", "", "MONTHLY", 2999, "USD", Map.of());
        SubscriptionContract contract = service.createSubscription("t1", "u1", "pro_monthly", 30);
        assertNotNull(contract);
        assertEquals("t1", contract.tenantId());
        assertEquals("u1", contract.userId());
        assertEquals("pro_monthly", contract.planKey());
        assertEquals("ACTIVE", contract.lifecycleState());
    }

    @Test
    void shouldThrowOnCreateSubscriptionWithUnknownPlan() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createSubscription("t1", "u1", "nonexistent", 30));
    }

    @Test
    void shouldGetCurrentSubscription() {
        service.createPlan("pro_monthly", "Pro Monthly", "", "MONTHLY", 2999, "USD", Map.of());
        service.createSubscription("t1", "u1", "pro_monthly", 30);
        SubscriptionContract current = service.getCurrentSubscription("t1", "u1");
        assertNotNull(current);
        assertEquals("ACTIVE", current.lifecycleState());
    }

    @Test
    void shouldReturnNullWhenNoCurrentSubscription() {
        SubscriptionContract current = service.getCurrentSubscription("t1", "u1");
        assertNull(current);
    }

    @Test
    void shouldChangePlan() {
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 2999, "USD", Map.of());
        service.createPlan("team_monthly", "Team", "", "MONTHLY", 9999, "USD", Map.of());
        SubscriptionContract contract = service.createSubscription("t1", "u1", "pro_monthly", 30);
        SubscriptionContract changed = service.changePlan(contract.contractId(), "team_monthly", 30);
        assertEquals("team_monthly", changed.planKey());
        assertEquals(9999, changed.basePriceMinor());
    }

    @Test
    void shouldThrowOnChangePlanForUnknownContract() {
        assertThrows(IllegalArgumentException.class, () ->
                service.changePlan("nonexistent", "pro_monthly", 30));
    }

    @Test
    void shouldThrowOnChangePlanForUnknownPlan() {
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 2999, "USD", Map.of());
        SubscriptionContract contract = service.createSubscription("t1", "u1", "pro_monthly", 30);
        assertThrows(IllegalArgumentException.class, () ->
                service.changePlan(contract.contractId(), "nonexistent", 30));
    }

    @Test
    void shouldCancelAtPeriodEnd() {
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 2999, "USD", Map.of());
        SubscriptionContract contract = service.createSubscription("t1", "u1", "pro_monthly", 30);
        SubscriptionContract cancelled = service.cancelAtPeriodEnd(contract.contractId());
        assertEquals("CANCELLED", cancelled.lifecycleState());
    }

    @Test
    void shouldThrowOnCancelUnknownContract() {
        assertThrows(IllegalArgumentException.class, () ->
                service.cancelAtPeriodEnd("nonexistent"));
    }

    @Test
    void shouldNotReturnCancelledAsCurrent() {
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 2999, "USD", Map.of());
        SubscriptionContract contract = service.createSubscription("t1", "u1", "pro_monthly", 30);
        service.cancelAtPeriodEnd(contract.contractId());
        SubscriptionContract current = service.getCurrentSubscription("t1", "u1");
        assertNull(current);
    }

    @Test
    void shouldProcessBillingCycle() {
        assertDoesNotThrow(() -> service.processBillingCycle());
    }

    @Test
    void shouldSupportBaseAndAddonSubscriptionsTogether() {
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 9999, "USD",
                Map.of("render.minutes", 600L));
        service.createPlan("addon_gpu_monthly", "GPU", "", "MONTHLY", 4999, "USD",
                Map.of("gpu.minutes", 300L));

        service.createSubscription("t1", "u1", "pro_monthly", "pro_monthly", 30,
                SubscriptionContractRole.BASE);
        service.createAddonSubscription("t1", "u1", "addon_gpu_monthly", "addon_gpu_monthly", 30);

        List<SubscriptionContract> active = service.listActiveSubscriptions("t1", "u1");
        assertEquals(2, active.size());
        assertNotNull(service.getCurrentSubscription("t1", "u1"));
        assertEquals(600L, service.getEffectiveIncludedQuota("t1", "u1").get("render.minutes"));
        assertEquals(300L, service.getEffectiveIncludedQuota("t1", "u1").get("gpu.minutes"));
    }

    @Test
    void newBaseSubscriptionReplacesPreviousBase() {
        service.createPlan("basic_monthly", "Basic", "", "MONTHLY", 2999, "USD", Map.of());
        service.createPlan("pro_monthly", "Pro", "", "MONTHLY", 9999, "USD", Map.of());
        service.createSubscription("t1", "u1", "basic_monthly", "basic_monthly", 30,
                SubscriptionContractRole.BASE);
        service.createSubscription("t1", "u1", "pro_monthly", "pro_monthly", 30,
                SubscriptionContractRole.BASE);

        List<SubscriptionContract> active = service.listActiveSubscriptions("t1", "u1");
        long activeBase = active.stream()
                .filter(c -> c.contractRole() == SubscriptionContractRole.BASE)
                .count();
        assertEquals(1, activeBase);
        assertEquals("pro_monthly", service.getCurrentSubscription("t1", "u1").planKey());
    }
}
