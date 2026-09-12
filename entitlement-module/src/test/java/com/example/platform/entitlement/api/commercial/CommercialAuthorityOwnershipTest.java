package com.example.platform.entitlement.api.commercial;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommercialAuthorityOwnershipTest {
    @Test void retiredSharedApplicationDefinitionsCannotBeLoaded() {
        for (String name : List.of("CommercialAdmissionPort", "CommercialAdmissionRequest", "CommercialDecision",
                "CommercialDecisionReason", "EntitlementDecision", "QuotaConsumptionPort", "QuotaConsumptionRequest",
                "QuotaDecision", "CommercialPrice", "CommercialValidation", "EntitlementDecisionProjection",
                "ExecutionCostProjection", "QuotaDecisionProjection")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName("com.example.platform.shared.commercial." + name), name);
        }
    }
    @Test void sharedValuesRemainSharedAndApplicationPortsHaveOneEntitlementOwner() {
        assertEquals("com.example.platform.shared.commercial", com.example.platform.shared.commercial.PrincipalType.class.getPackageName());
        assertEquals("com.example.platform.shared.commercial", com.example.platform.shared.commercial.Money.class.getPackageName());
        for (Class<?> type : List.of(CommercialAdmissionPort.class, QuotaConsumptionPort.class,
                CommercialDecision.class, EntitlementDecision.class, QuotaDecision.class)) {
            assertEquals("com.example.platform.entitlement.api.commercial", type.getPackageName());
        }
    }
}
