package com.example.platform.policy.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.policy.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagAuditService;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class PolicyEvaluationServiceFeatureFlagTest {

    private PolicyEvaluationService service;
    private FeatureFlagService featureFlagService;
    private FeatureFlagAuditService auditService;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        auditService = mock(FeatureFlagAuditService.class);
        service = new PolicyEvaluationService(featureFlagService, auditService);
    }

    @Test
    void evaluateWithDisabledFeatureFlagReturnsDeny() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"export.gpu.v2.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.v2.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertNotNull(decision.matchedFeatureFlags());
        assertFalse(decision.matchedFeatureFlags().get("export.gpu.v2.enabled"));
        verify(auditService).auditEvaluated(any(FeatureFlagDecision.class), eq("user-1"));
    }

    @Test
    void evaluateWithEnabledFeatureFlagReturnsAllow() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"export.gpu.v2.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.v2.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("rule-ff", decision.matchedRuleId());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().get("export.gpu.v2.enabled"));
    }

    @Test
    void evaluateWithFeatureFlagPrefixStripped() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"featureFlag.export.gpu.v2.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0);
                    assertEquals("export.gpu.v2.enabled", req.flagKey());
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision("export.gpu.v2.enabled", true, "enabled",
                                    "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", null, "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        service.evaluate(context);

        verify(featureFlagService).evaluate(argThat(req ->
                "export.gpu.v2.enabled".equals(req.flagKey())));
    }

    @Test
    void evaluateWithStringExpectedValue() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"graphql.queryAggregation.enabled\",\"operator\":\"eq\",\"expectedValue\":\"true\"}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("graphql.queryAggregation.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void evaluateWithNotEqualsOperator() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"some.flag\",\"operator\":\"ne\",\"expectedValue\":false}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("some.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void evaluateIncludesAllMatchedFlagsInDecision() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "Feature Flag Rule",
                PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag1\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("flag1", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(1, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().containsKey("flag1"));
    }

    @Test
    void evaluateWithNoFeatureFlagConditionFallsThrough() {
        PolicyRule rule = new PolicyRule(
                "rule-tenant", "Tenant Rule",
                PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertTrue(decision.matchedFeatureFlags().isEmpty());
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void existingDefaultDenyStillWorks() {
        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-default-deny", decision.matchedRuleId());
    }

    @Test
    void listRulesReturnsAllRules() {
        List<PolicyRule> rules = service.listRules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> "rule-default-deny".equals(r.id())));
    }

    @Test
    void removeRulePreventsMatching() {
        PolicyRule rule = new PolicyRule(
                "rule-remove", "Remove",
                PolicyEffect.ALLOW,
                "{}",
                5, "ACTIVE");
        service.addRule(rule);
        service.removeRule("rule-remove");

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.DENY, decision.effect());
    }

    @Test
    void policyFeatureFlagConditionRecord() {
        PolicyFeatureFlagCondition condition = new PolicyFeatureFlagCondition(
                "export.gpu.v2.enabled", "eq", true);
        assertEquals("export.gpu.v2.enabled", condition.flagKey());
        assertEquals("eq", condition.operator());
        assertEquals(true, condition.expectedValue());
    }

    @Test
    void policyDecisionBackwardCompatibility() {
        PolicyDecision decision = new PolicyDecision(
                PolicyEffect.ALLOW, "test", "rule-1");
        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("test", decision.reason());
        assertEquals("rule-1", decision.matchedRuleId());
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().isEmpty());
    }

    @Test
    void policyDecisionWithFeatureFlags() {
        PolicyDecision decision = new PolicyDecision(
                PolicyEffect.ALLOW, "test", "rule-1",
                Map.of("flag1", true, "flag2", false));
        assertEquals(2, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().get("flag1"));
        assertFalse(decision.matchedFeatureFlags().get("flag2"));
    }
}
