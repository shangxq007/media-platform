package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UsageRedMatrixTest {

    @Test
    void observationHasNoCommercialField() {
        String fields = Arrays.stream(ObservedRuntimeUsage.class.getRecordComponents())
                .map(RecordComponent::getName).reduce("", (left, right) -> left + " " + right);
        for (String forbidden : new String[] {
                "price", "billable", "entitlement", "subscription", "quota", "currency"}) {
            assertFalse(fields.toLowerCase().contains(forbidden));
        }
    }

    @Test
    void billableLineageHasEveryMandatoryEvidenceField() {
        var names = Arrays.stream(BillableUsage.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertTrue(names.containsAll(java.util.List.of(
                "observedUsageId", "observedQuantity", "billableMeter", "billableQuantity",
                "meteringRuleId", "meteringRuleVersion", "transformationKind",
                "transformationDetails", "sourceObservationTimestamp", "meteredAt",
                "idempotencyKey", "traceId", "provenanceReference")));
    }

    @Test
    void meteringPublicApiAcceptsExistingObservationIdentityNotQuantity() {
        assertTrue(Arrays.stream(UsageMeteringService.class.getMethods())
                .filter(method -> method.getName().equals("meter"))
                .allMatch(method -> method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].equals(MeterUsageCommand.class)));
        assertFalse(Arrays.stream(UsageMeteringService.class.getMethods())
                .anyMatch(method -> method.getName().equals("recordUsage")));
    }
}
