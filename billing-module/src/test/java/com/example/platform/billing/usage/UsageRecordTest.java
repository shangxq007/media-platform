package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.usage.ObservedRuntimeUsage;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class UsageRecordTest {

    @Test
    void legacyUsageRecordIsReadOnlyInterfaceWithNoFactoryOrWriter() {
        assertTrue(UsageRecord.class.isInterface());
        assertTrue(Modifier.isAbstract(UsageRecord.class.getModifiers()));
        assertFalse(java.util.Arrays.stream(UsageRecord.class.getMethods())
                .anyMatch(method -> method.getName().equals("record")
                        || method.getName().equals("save")
                        || method.getName().equals("emit")));
    }

    @Test
    void operationalAndBillableTypesRemainDistinct() {
        assertFalse(UsageRecord.class.isAssignableFrom(ObservedRuntimeUsage.class));
        assertTrue(UsageRecord.class.isAssignableFrom(BillableUsage.class));
    }
}
