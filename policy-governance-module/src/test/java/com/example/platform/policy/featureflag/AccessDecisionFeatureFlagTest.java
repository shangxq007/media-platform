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

class AccessDecisionFeatureFlagTest {

    private PolicyEvaluationService accessDecisionService;
    private FeatureFlagService featureFlagService;
    private FeatureFlagAuditService auditService;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        auditService = mock(FeatureFlagAuditService.class);
        accessDecisionService = new PolicyEvaluationService(featureFlagService, auditService);
    }

    @Test
    void accessAllowedWhenFeatureFlagEnabled() {
        PolicyRule rule = new PolicyRule(
                "access-gpu", "GPU Access", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"export.gpu.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "export", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertTrue(decision.matchedFeatureFlags().get("export.gpu.enabled"));
        verify(auditService).auditEvaluated(any(FeatureFlagDecision.class), eq("user-1"));
    }

    @Test
    void accessDeniedWhenFeatureFlagDisabled() {
        PolicyRule rule = new PolicyRule(
                "access-gpu", "GPU Access", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"export.gpu.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.gpu.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "export", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertFalse(decision.matchedFeatureFlags().get("export.gpu.enabled"));
    }

    @Test
    void accessDeniedByFeatureFlagAuditEvent() {
        PolicyRule rule = new PolicyRule(
                "access-4k", "4K Access", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"export.4k.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("export.4k.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "export", "api", Map.of());
        accessDecisionService.evaluate(context);

        verify(auditService).auditEvaluated(
                argThat(d -> "export.4k.enabled".equals(d.flagKey()) && !d.enabled()),
                eq("user-1"));
    }

    @Test
    void accessWithMultipleFeatureFlagsAllEnabled() {
        PolicyRule rule1 = new PolicyRule(
                "access-multi-1", "Multi 1", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag.a\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        PolicyRule rule2 = new PolicyRule(
                "access-multi-2", "Multi 2", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag.b\",\"operator\":\"eq\",\"expectedValue\":true}}",
                10, "ACTIVE");
        accessDecisionService.addRule(rule1);
        accessDecisionService.addRule(rule2);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0, FeatureFlagEvaluationRequest.class);
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision(req.flagKey(), true, "enabled",
                                    "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", "ws-1", "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "export", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("access-multi-1", decision.matchedRuleId());
    }

    @Test
    void accessWithMultipleFeatureFlagsFirstDisabled() {
        PolicyRule rule1 = new PolicyRule(
                "access-multi-deny", "Multi Deny", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"flag.x\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule1);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("flag.x", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "export", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertFalse(decision.matchedFeatureFlags().get("flag.x"));
    }

    @Test
    void accessDecisionIncludesAllEvaluatedFlags() {
        PolicyRule rule = new PolicyRule(
                "access-include-flags", "Include Flags", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"access.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("access.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertNotNull(decision.matchedFeatureFlags());
        assertEquals(1, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().containsKey("access.flag"));
    }

    @Test
    void accessWithNoFeatureFlagConditionDoesNotCallService() {
        PolicyRule rule = new PolicyRule(
                "access-tenant-only", "Tenant Only", PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void accessDefaultDenyWhenNoRulesMatch() {
        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-default-deny", decision.matchedRuleId());
    }

    @Test
    void accessDecisionWithNotEqualsOperator() {
        PolicyRule rule = new PolicyRule(
                "access-ne", "NE Access", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"blocked.feature\",\"operator\":\"ne\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("blocked.feature", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void accessDecisionWithEqualsOperatorAndDisabledFlag() {
        PolicyRule rule = new PolicyRule(
                "access-eq-deny", "EQ Deny", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"required.feature\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("required.feature", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        PolicyDecision decision = accessDecisionService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
    }

    @Test
    void accessDecisionRecordsCorrectUserId() {
        PolicyRule rule = new PolicyRule(
                "access-user-check", "User Check", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"user.feature\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        accessDecisionService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("user.feature", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-42", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-42", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());
        accessDecisionService.evaluate(context);

        verify(auditService).auditEvaluated(
                argThat(d -> "user.feature".equals(d.flagKey())),
                eq("user-42"));
    }
}
