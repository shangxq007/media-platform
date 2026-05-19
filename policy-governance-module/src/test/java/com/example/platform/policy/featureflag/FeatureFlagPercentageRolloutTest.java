package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagPercentageRolloutTest {

    private LocalFeatureFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "pct-flag", "Percentage Flag", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
    }

    @Test
    void zeroPercentEnablesNoOne() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-0", "pct-flag", 10, true,
                null, null, null, null, null, null,
                0.0, null, null, null, null, null
        ));
        int enabled = 0;
        for (int i = 0; i < 100; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertEquals(0, enabled);
    }

    @Test
    void hundredPercentEnablesEveryone() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-100", "pct-flag", 10, true,
                null, null, null, null, null, null,
                100.0, null, null, null, null, null
        ));
        int enabled = 0;
        for (int i = 0; i < 100; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertEquals(100, enabled);
    }

    @Test
    void fiftyPercentEnablesApproximatelyHalf() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-50", "pct-flag", 10, true,
                null, null, null, null, null, null,
                50.0, null, null, null, null, null
        ));
        int enabled = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertTrue(enabled > 300 && enabled < 700,
                "Expected ~500 enabled out of 1000, got " + enabled);
    }

    @Test
    void twentyFivePercentEnablesApproximatelyQuarter() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-25", "pct-flag", 10, true,
                null, null, null, null, null, null,
                25.0, null, null, null, null, null
        ));
        int enabled = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertTrue(enabled > 150 && enabled < 350,
                "Expected ~250 enabled out of 1000, got " + enabled);
    }

    @Test
    void rolloutIsDeterministicPerUser() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-det", "pct-flag", 10, true,
                null, null, null, null, null, null,
                50.0, null, null, null, null, null
        ));
        for (int i = 0; i < 100; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision d1 = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            FeatureFlagDecision d2 = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            assertEquals(d1.enabled(), d2.enabled(),
                    "User " + i + " should get consistent results");
        }
    }

    @Test
    void rolloutUsesTenantIdWhenNoUserId() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-tenant-hash", "pct-flag", 10, true,
                null, null, null, null, null, null,
                50.0, null, null, null, null, null
        ));
        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-hash", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision d1 = provider.evaluate(
                new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
        FeatureFlagDecision d2 = provider.evaluate(
                new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
        assertEquals(d1.enabled(), d2.enabled());
    }

    @Test
    void combinedTenantAndPercentageRollout() {
        LocalFeatureFlagProvider p = new LocalFeatureFlagProvider();
        p.saveFlag(new FeatureFlagDefinition(
                "combo-flag", "Combo", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        p.saveRule("combo-flag", new FeatureFlagTargetingRule(
                "r-combo", "combo-flag", 10, true,
                "acme", null, null, null, null, null,
                50.0, null, null, null, null, null
        ));

        int enabled = 0;
        int total = 100;
        for (int i = 0; i < total; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    "acme", null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = p.evaluate(
                    new FeatureFlagEvaluationRequest("combo-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertTrue(enabled > 0 && enabled < total);
    }

    @Test
    void rolloutWithUnknownUserFallsBackToRandom() {
        provider.saveRule("pct-flag", new FeatureFlagTargetingRule(
                "r-fallback", "pct-flag", 10, true,
                null, null, null, null, null, null,
                50.0, null, null, null, null, null
        ));
        FeatureFlagContext ctx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        assertDoesNotThrow(() -> provider.evaluate(
                new FeatureFlagEvaluationRequest("pct-flag", ctx, false)));
    }
}
