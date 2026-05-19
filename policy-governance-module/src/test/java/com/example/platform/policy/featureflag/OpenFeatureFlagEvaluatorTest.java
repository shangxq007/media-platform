package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenFeatureFlagEvaluatorTest {

    private OpenFeatureFlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new OpenFeatureFlagEvaluator();
    }

    @Test
    void evaluateBooleanFlagWithTrueDefault() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "nonexistent-boolean-flag", context, true);
        assertNotNull(decision);
        assertEquals("nonexistent-boolean-flag", decision.flagKey());
        assertEquals(FeatureFlagProviderType.OPENFEATURE, decision.providerType());
        assertNotNull(decision.evaluatedAt());
    }

    @Test
    void evaluateBooleanFlagWithFalseDefault() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "nonexistent-boolean-flag", context, false);
        assertNotNull(decision);
        assertEquals("nonexistent-boolean-flag", decision.flagKey());
        assertFalse(decision.enabled());
    }

    @Test
    void evaluateStringFlag() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateString(
                "nonexistent-string-flag", context, "default-value");
        assertNotNull(decision);
        assertEquals("nonexistent-string-flag", decision.flagKey());
        assertEquals(FeatureFlagProviderType.OPENFEATURE, decision.providerType());
    }

    @Test
    void evaluateNumberFlag() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateNumber(
                "nonexistent-number-flag", context, 0.0);
        assertNotNull(decision);
        assertEquals("nonexistent-number-flag", decision.flagKey());
        assertEquals(FeatureFlagProviderType.OPENFEATURE, decision.providerType());
    }

    @Test
    void evaluateWithNullContext() {
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "null-context-flag", null, true);
        assertNotNull(decision);
        assertEquals("null-context-flag", decision.flagKey());
    }

    @Test
    void evaluateWithFullContext() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN"), List.of("group-1"),
                "enterprise", "api", "prod", "us-east", "low",
                Map.of("custom", "value"));
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "full-context-flag", context, true);
        assertNotNull(decision);
        assertEquals("tenant-1", decision.tenantId());
        assertEquals("ws-1", decision.workspaceId());
        assertEquals("user-1", decision.userId());
    }

    @Test
    void evaluateWithEmptyContext() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "empty-context-flag", context, false);
        assertNotNull(decision);
        assertFalse(decision.enabled());
    }

    @Test
    void evaluateReturnsDetailsMap() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1",
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "details-flag", context, true);
        assertNotNull(decision.details());
        assertTrue(decision.details().containsKey("flagKey"));
        assertTrue(decision.details().containsKey("provider"));
    }

    @Test
    void evaluateReturnsDetailsWithTenantId() {
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "tenant-details-flag", context, true);
        assertNotNull(decision.details());
        assertEquals("tenant-1", decision.details().get("tenantId"));
    }

    @Test
    void evaluateDecisionContainsReasonCode() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "reason-flag", context, true);
        assertNotNull(decision.reasonCode());
        assertEquals("EVALUATED", decision.reasonCode());
    }

    @Test
    void evaluateStringFlagWithEmptyDefault() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateString(
                "empty-string-flag", context, "");
        assertNotNull(decision);
    }

    @Test
    void evaluateNumberFlagWithPositiveDefault() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateNumber(
                "positive-number-flag", context, 3.14);
        assertNotNull(decision);
    }

    @Test
    void evaluateBooleanFlagWithNullDefault() {
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "null-default-flag", null, null);
        FeatureFlagDecision decision = evaluator.evaluate(request);
        assertNotNull(decision);
        assertEquals("null-default-flag", decision.flagKey());
    }

    @Test
    void evaluateBooleanFlagDefaultsToDisabled() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "default-disabled-flag", context, false);
        assertFalse(decision.enabled());
        assertEquals("disabled", decision.variant());
    }

    @Test
    void evaluateBooleanFlagDefaultsToEnabled() {
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null,
                List.of(), List.of(), null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = evaluator.evaluateBoolean(
                "default-enabled-flag", context, true);
        assertTrue(decision.enabled());
        assertEquals("enabled", decision.variant());
    }
}
