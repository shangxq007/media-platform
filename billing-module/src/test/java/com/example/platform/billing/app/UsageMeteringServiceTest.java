package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.billing.usage.MeteringRule;
import com.example.platform.billing.usage.MeteringRuleRegistry;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageUnit;
import org.junit.jupiter.api.Test;

class UsageMeteringServiceTest {

    @Test
    void integralRuleRoundsUpWithoutFloatingPoint() {
        MeteringRule rule = durationRule();
        assertEquals(0, rule.transform(0));
        assertEquals(1, rule.transform(1));
        assertEquals(1, rule.transform(1_000));
        assertEquals(2, rule.transform(1_001));
    }

    @Test
    void ruleRegistryHasNoUnknownOrDefaultFallback() {
        MeteringRuleRegistry registry = new MeteringRuleRegistry();
        assertTrue(registry.find("missing", "v1").isEmpty());
        registry.register(durationRule());
        assertTrue(registry.find("duration", "v1").isPresent());
        assertTrue(registry.find("duration", "v2").isEmpty());
    }

    @Test
    void arithmeticOverflowFailsClosed() {
        MeteringRule rule = new MeteringRule(
                "overflow", "v1", UsageDimension.DURATION, UsageUnit.MILLISECONDS,
                "seconds", UsageDimension.DURATION, UsageUnit.SECONDS,
                2, 1, 1, MeteringTransformationKind.SCALE, "overflow proof");
        assertThrows(ArithmeticException.class, () -> rule.transform(Long.MAX_VALUE));
    }

    private static MeteringRule durationRule() {
        return new MeteringRule(
                "duration", "v1", UsageDimension.DURATION, UsageUnit.MILLISECONDS,
                "seconds", UsageDimension.DURATION, UsageUnit.SECONDS,
                1, 1_000, 1, MeteringTransformationKind.ROUND_UP_INCREMENT,
                "milliseconds to seconds");
    }
}
