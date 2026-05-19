package com.example.platform.policy.featureflag;

import com.example.platform.policy.app.PolicyEvaluationService;
import com.example.platform.policy.domain.*;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyEvaluationWithFeatureFlagTest {

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
    void policyAllowWithEnabledFeatureFlag() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "FF Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"my.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("my.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("rule-ff", decision.matchedRuleId());
        assertTrue(decision.matchedFeatureFlags().get("my.flag"));
    }

    @Test
    void policyDenyWithDisabledFeatureFlag() {
        PolicyRule rule = new PolicyRule(
                "rule-ff", "FF Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"my.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("my.flag", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertFalse(decision.matchedFeatureFlags().get("my.flag"));
    }

    @Test
    void policyWithNotEqualsOperatorAndDisabledFlagAllows() {
        PolicyRule rule = new PolicyRule(
                "rule-ne", "NE Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"blocked.flag\",\"operator\":\"ne\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("blocked.flag", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithNotEqualsOperatorAndEnabledFlagDenies() {
        PolicyRule rule = new PolicyRule(
                "rule-ne2", "NE2 Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"blocked.flag\",\"operator\":\"ne\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("blocked.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
    }

    @Test
    void policyWithMultipleFeatureFlags() {
        PolicyRule rule1 = new PolicyRule(
                "rule-ff1", "FF1 Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag1\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        PolicyRule rule2 = new PolicyRule(
                "rule-ff2", "FF2 Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag2\",\"operator\":\"eq\",\"expectedValue\":true}}",
                10, "ACTIVE");
        service.addRule(rule1);
        service.addRule(rule2);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0, FeatureFlagEvaluationRequest.class);
                    if ("flag1".equals(req.flagKey())) {
                        return new FeatureFlagEvaluationResult(
                                new FeatureFlagDecision("flag1", true, "enabled",
                                        "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                        "tenant-1", null, "user-1", Instant.now(), Map.of()));
                    }
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision("flag2", false, "disabled",
                                    "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", null, "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("rule-ff1", decision.matchedRuleId());
    }

    @Test
    void policyWithStringExpectedValueTrue() {
        PolicyRule rule = new PolicyRule(
                "rule-str", "String Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"str.flag\",\"operator\":\"eq\",\"expectedValue\":\"true\"}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("str.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithStringExpectedValueFalse() {
        PolicyRule rule = new PolicyRule(
                "rule-str-false", "String False Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"str.flag\",\"operator\":\"eq\",\"expectedValue\":\"false\"}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("str.flag", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyAuditCalledOnFeatureFlagEvaluation() {
        PolicyRule rule = new PolicyRule(
                "rule-audit", "Audit Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"audit.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("audit.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        service.evaluate(context);

        verify(auditService).auditEvaluated(any(FeatureFlagDecision.class), eq("user-1"));
    }

    @Test
    void policyWithFeatureFlagPrefixStrippedFromFlagKey() {
        PolicyRule rule = new PolicyRule(
                "rule-prefix", "Prefix Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"featureFlag.my.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0, FeatureFlagEvaluationRequest.class);
                    assertEquals("my.flag", req.flagKey());
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision("my.flag", true, "enabled",
                                    "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", null, "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        service.evaluate(context);

        verify(featureFlagService).evaluate(argThat((FeatureFlagEvaluationRequest req) ->
                "my.flag".equals(req.flagKey())));
    }

    @Test
    void policyWithNoFeatureFlagConditionDoesNotCallService() {
        PolicyRule rule = new PolicyRule(
                "rule-tenant", "Tenant Rule", PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void policyDefaultDenyWithNoRules() {
        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-default-deny", decision.matchedRuleId());
    }

    @Test
    void policyFeatureFlagConditionRecord() {
        PolicyFeatureFlagCondition condition = new PolicyFeatureFlagCondition(
                "test.flag", "eq", true);
        assertEquals("test.flag", condition.flagKey());
        assertEquals("eq", condition.operator());
        assertEquals(true, condition.expectedValue());
    }

    @Test
    void policyDecisionWithFeatureFlagsMap() {
        PolicyDecision decision = new PolicyDecision(
                PolicyEffect.ALLOW, "matched", "rule-1",
                Map.of("flag1", true, "flag2", false));
        assertEquals(2, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().get("flag1"));
        assertFalse(decision.matchedFeatureFlags().get("flag2"));
    }

    @Test
    void policyDecisionBackwardCompatibility() {
        PolicyDecision decision = new PolicyDecision(
                PolicyEffect.ALLOW, "test", "rule-1");
        assertNotNull(decision.matchedFeatureFlags());
        assertTrue(decision.matchedFeatureFlags().isEmpty());
    }

    @Test
    void policyWithNullOperatorDefaultsToEq() {
        PolicyRule rule = new PolicyRule(
                "rule-null-op", "Null Op Rule", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"null.op.flag\",\"operator\":null,\"expectedValue\":true}}",
                5, "ACTIVE");
        service.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("null.op.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", null, "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithEmptyConditionsMatchesAll() {
        PolicyRule rule = new PolicyRule(
                "rule-empty", "Empty Rule", PolicyEffect.ALLOW,
                "{}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithTenantIdConditionMatches() {
        PolicyRule rule = new PolicyRule(
                "rule-tenant-match", "Tenant Match", PolicyEffect.ALLOW,
                "{\"tenantId\":\"acme\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "acme", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithWorkspaceIdConditionMatches() {
        PolicyRule rule = new PolicyRule(
                "rule-ws-match", "WS Match", PolicyEffect.ALLOW,
                "{\"workspaceId\":\"ws-42\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-42", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithUserIdConditionMatches() {
        PolicyRule rule = new PolicyRule(
                "rule-user-match", "User Match", PolicyEffect.ALLOW,
                "{\"userId\":\"user-42\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-42", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithRoleConditionMatches() {
        PolicyRule rule = new PolicyRule(
                "rule-role-match", "Role Match", PolicyEffect.ALLOW,
                "{\"role\":\"SUPER_ADMIN\"}",
                5, "ACTIVE");
        service.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "SUPER_ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void policyWithRemoveRulePreventsMatching() {
        PolicyRule rule = new PolicyRule(
                "rule-removable", "Removable", PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        service.addRule(rule);
        service.removeRule("rule-removable");

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
    }

    @Test
    void policyListRulesReturnsAllRules() {
        List<PolicyRule> rules = service.listRules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> "rule-default-deny".equals(r.id())));
    }

    @Test
    void policyPriorityOrdering() {
        PolicyRule lowPriority = new PolicyRule(
                "rule-low", "Low", PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                100, "ACTIVE");
        PolicyRule highPriority = new PolicyRule(
                "rule-high", "High", PolicyEffect.DENY,
                "{\"tenantId\":\"tenant-1\"}",
                1, "ACTIVE");
        service.addRule(lowPriority);
        service.addRule(highPriority);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1", "feature", "api", Map.of());
        PolicyDecision decision = service.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-high", decision.matchedRuleId());
    }
}
