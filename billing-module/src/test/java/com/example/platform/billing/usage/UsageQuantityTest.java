package com.example.platform.billing.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsageQuantityTest {

    @Test
    void fromBaseUnits_acceptsValidUnit() {
        UsageQuantity q = UsageQuantity.fromBaseUnits(1000, UsageUnit.MILLISECONDS);
        assertEquals(1000, q.baseUnits());
        assertEquals(UsageUnit.MILLISECONDS, q.unit());
    }

    @Test
    void fromBaseUnits_zeroIsValid() {
        UsageQuantity q = UsageQuantity.fromBaseUnits(0, UsageUnit.COUNT);
        assertEquals(0, q.baseUnits());
    }

    @Test
    void fromBaseUnits_rejectsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> UsageQuantity.fromBaseUnits(-1, UsageUnit.BYTE));
    }

    @Test
    void fromBaseUnits_rejectsNullUnit() {
        assertThrows(NullPointerException.class,
                () -> UsageQuantity.fromBaseUnits(10, null));
    }

    @Test
    void validate_acceptsLegalPairings() {
        UsageUnit.validate(UsageDimension.REQUEST, UsageUnit.COUNT);
        UsageUnit.validate(UsageDimension.DURATION, UsageUnit.MILLISECONDS);
        UsageUnit.validate(UsageDimension.DURATION, UsageUnit.SECONDS);
        UsageUnit.validate(UsageDimension.BYTE_STORED, UsageUnit.BYTE);
        UsageUnit.validate(UsageDimension.BYTE_EGRESS, UsageUnit.BYTE);
        UsageUnit.validate(UsageDimension.DELIVERY_BYTE, UsageUnit.BYTE);
        UsageUnit.validate(UsageDimension.TOKEN_INPUT, UsageUnit.TOKEN);
        UsageUnit.validate(UsageDimension.TOKEN_OUTPUT, UsageUnit.TOKEN);
    }

    @Test
    void validate_rejectsIllegalPairing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UsageUnit.validate(UsageDimension.REQUEST, UsageUnit.TOKEN));
        assertTrue(ex.getMessage().contains("REQUEST"));
    }

    @Test
    void validate_rejectsByteUnitForRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> UsageUnit.validate(UsageDimension.REQUEST, UsageUnit.BYTE));
    }

    @Test
    void validate_rejectsNullDimension() {
        assertThrows(NullPointerException.class,
                () -> UsageUnit.validate(null, UsageUnit.COUNT));
    }

    @Test
    void validate_rejectsNullUnit() {
        assertThrows(NullPointerException.class,
                () -> UsageUnit.validate(UsageDimension.REQUEST, null));
    }
}
