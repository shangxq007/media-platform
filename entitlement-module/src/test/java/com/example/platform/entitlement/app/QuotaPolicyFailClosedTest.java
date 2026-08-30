package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuotaPolicyFailClosedTest {

    @Test
    void unknownQuotaKeyCannotReceiveAnInventedDefaultLimit() {
        QuotaPolicyService service = new QuotaPolicyService();
        assertThrows(IllegalArgumentException.class,
                () -> service.getQuotaPolicy("unknown.capability"));
    }

    @Test
    void processLocalMapIsNotCommercialLimitAuthority() {
        assertFalse(Arrays.stream(QuotaPolicyService.class.getDeclaredFields())
                .anyMatch(field -> Map.class.isAssignableFrom(field.getType())));
    }
}
