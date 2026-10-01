package com.example.platform.operation.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationModelLayerTypesTest {

    @Test
    void operationEffectIsAClosedFixedVocabulary() {
        assertEquals(3, OperationEffect.values().length);
        assertEquals(OperationEffect.NONE, OperationEffect.valueOf("NONE"));
        assertEquals(OperationEffect.COVER_OF, OperationEffect.valueOf("COVER_OF"));
        assertEquals(OperationEffect.THUMBNAIL, OperationEffect.valueOf("THUMBNAIL"));
    }

    @Test
    void costCapUsesBigDecimalAndRejectsNegative() {
        CostCap cap = new CostCap(new BigDecimal("0.1"), "quota-unit");
        assertEquals(new BigDecimal("0.1"), cap.units());
        assertThrows(IllegalArgumentException.class, () -> new CostCap(new BigDecimal("-1"), "quota-unit"));
        assertThrows(IllegalArgumentException.class, () -> new CostCap(BigDecimal.ONE, " "));
        assertThrows(NullPointerException.class, () -> new CostCap(null, "quota-unit"));
    }

    @Test
    void deadlineMustBeBoundedPositive() {
        assertEquals(Duration.ofSeconds(30), new Deadline(Duration.ofSeconds(30)).maxWallClock());
        assertThrows(IllegalArgumentException.class, () -> new Deadline(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new Deadline(Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> new Deadline(null));
    }

    @Test
    void executionRequirementsRequireAllProviderNeutralFields() {
        ExecutionRequirements requirements = ExecutionRequirements.defaults();
        assertEquals(ExecutionMode.ASYNCHRONOUS_DURABLE, requirements.executionMode());
        assertInstanceOf(CostCap.class, requirements.costCap());
        assertInstanceOf(Deadline.class, requirements.deadline());
        assertEquals(SelectionObjective.PREFER_DETERMINISTIC, requirements.selectionObjective());
        assertThrows(NullPointerException.class, () -> new ExecutionRequirements(
                null, requirements.costCap(), requirements.deadline(), requirements.selectionObjective()));
    }

    @Test
    void selectionPolicyIsProviderNeutralAndSealed() {
        assertInstanceOf(SelectionPolicy.Cheapest.class, SelectionPolicy.Cheapest.INSTANCE);
        assertInstanceOf(SelectionPolicy.Fastest.class, SelectionPolicy.Fastest.INSTANCE);
        assertInstanceOf(SelectionPolicy.PreferDeterministic.class, SelectionPolicy.PreferDeterministic.INSTANCE);
        assertEquals("impl-1", new SelectionPolicy.PinnedInternal("impl-1").implementationId());
        assertThrows(IllegalArgumentException.class, () -> new SelectionPolicy.PinnedInternal(" "));
        assertFalse(Map.class.isAssignableFrom(SelectionPolicy.class));
    }

    @Test
    void basePinMirrorsExactBaseSemantics() {
        BasePin pin = new BasePin("rev-1", null);
        assertEquals("rev-1", pin.baseRevisionId());
        assertThrows(IllegalArgumentException.class, () -> new BasePin(" ", null));
        assertThrows(NullPointerException.class, () -> new BasePin(null, null));
    }
}
