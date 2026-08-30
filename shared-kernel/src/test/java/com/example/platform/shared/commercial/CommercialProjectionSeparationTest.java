package com.example.platform.shared.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class CommercialProjectionSeparationTest {

    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-1", PrincipalType.USER, "user-1");
    private static final Instant DECIDED_AT = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void entitlementAndQuotaDecisionsAreStructurallySeparate() {
        assertTrue(EntitlementDecision.class.isRecord());
        assertTrue(QuotaDecision.class.isRecord());
        assertFalse(EntitlementDecision.class.isAssignableFrom(QuotaDecision.class));
        assertFalse(QuotaDecision.class.isAssignableFrom(EntitlementDecision.class));

        Set<String> entitlementComponents = componentNames(EntitlementDecision.class);
        Set<String> quotaComponents = componentNames(QuotaDecision.class);
        assertTrue(entitlementComponents.contains("entitlementKey"));
        assertFalse(entitlementComponents.contains("requestedUnits"));
        assertFalse(entitlementComponents.contains("limitUnits"));
        assertTrue(quotaComponents.containsAll(Set.of(
                "quotaKey", "requestedUnits", "limitUnits", "usedUnits")));
        assertNotEquals(entitlementComponents, quotaComponents);
    }

    @Test
    void typedProjectionPortsReturnTheirOwnDecisionSurfaces() throws Exception {
        assertEquals(EntitlementDecision.class,
                EntitlementDecisionProjection.class
                        .getMethod("decide", PrincipalRef.class, String.class)
                        .getReturnType());
        assertEquals(QuotaDecision.class,
                QuotaDecisionProjection.class
                        .getMethod("decide", PrincipalRef.class, String.class, long.class)
                        .getReturnType());
    }

    @Test
    void executionCostInputRemainsDistinctFromCommercialPriceAndMoney() {
        Money technicalAmount = new Money(800L, "USD");
        ExecutionCostProjection cost = new ExecutionCostProjection(
                "execution-1", technicalAmount, "worker-fabric", "cost-v3", DECIDED_AT);
        CommercialPrice price = new CommercialPrice(
                new Money(1200L, "USD"), "pricing-v9", List.of());

        assertSame(technicalAmount, cost.technicalCost());
        assertEquals("worker-fabric", cost.costAuthority());
        assertFalse(Money.class.isAssignableFrom(ExecutionCostProjection.class));
        assertFalse(CommercialPrice.class.isAssignableFrom(ExecutionCostProjection.class));
        assertFalse(ExecutionCostProjection.class.isAssignableFrom(CommercialPrice.class));
        assertNotEquals(cost.technicalCost(), price.amount());
    }

    private static Set<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }
}
