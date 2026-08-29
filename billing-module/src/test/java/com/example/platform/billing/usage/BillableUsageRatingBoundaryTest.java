package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.platform.billing.app.RatingEngine;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BillableUsageRatingBoundaryTest {

    @Test
    void billingConsumptionAndRatingAcceptBillableUsageOnly() throws Exception {
        assertArrayEquals(
                new Class<?>[] {BillableUsage.class},
                BillingConsumptionBoundary.class.getMethod("consume", BillableUsage.class)
                        .getParameterTypes());
        assertArrayEquals(
                new Class<?>[] {BillableUsage.class,
                        com.example.platform.billing.domain.PricingRule.class},
                RatingEngine.class.getMethod(
                                "rateUsage", BillableUsage.class,
                                com.example.platform.billing.domain.PricingRule.class)
                        .getParameterTypes());
        assertNotEquals(ObservedRuntimeUsage.class, BillableUsage.class);
    }

    @Test
    void noRatingOverloadAcceptsObservedOrLegacyUsage() {
        boolean forbidden = Arrays.stream(RatingEngine.class.getMethods())
                .filter(method -> method.getName().equals("rateUsage"))
                .map(method -> method.getParameterTypes()[0])
                .anyMatch(type -> type.equals(ObservedRuntimeUsage.class)
                        || type.getSimpleName().equals("UsageRecord"));
        assertNotEquals(true, forbidden);
    }
}
