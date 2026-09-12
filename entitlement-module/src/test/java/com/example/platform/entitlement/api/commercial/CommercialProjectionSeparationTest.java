package com.example.platform.entitlement.api.commercial;

import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.Money;

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
    void actualPublishedPortsReturnTheirOwnDecisionSurfaces() throws Exception {
        assertEquals(CommercialDecision.class,
                CommercialAdmissionPort.class
                        .getMethod("decide", CommercialAdmissionRequest.class)
                        .getReturnType());
        assertEquals(QuotaDecision.class,
                QuotaConsumptionPort.class
                        .getMethod("consume", QuotaConsumptionRequest.class)
                        .getReturnType());
    }

    @Test
    void decisionsDoNotBecomeMoneyOrRawUsage() {
        assertFalse(componentNames(CommercialDecision.class).contains("price"));
        assertFalse(componentNames(QuotaDecision.class).contains("observedUsage"));
        assertFalse(componentNames(Money.class).contains("allowed"));
        assertFalse(Money.class.isAssignableFrom(CommercialDecision.class));
    }

    private static Set<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }
}
