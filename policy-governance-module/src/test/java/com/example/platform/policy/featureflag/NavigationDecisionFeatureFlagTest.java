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

class NavigationDecisionFeatureFlagTest {

    private PolicyEvaluationService navigationService;
    private FeatureFlagService featureFlagService;
    private FeatureFlagAuditService auditService;

    @BeforeEach
    void setUp() {
        featureFlagService = mock(FeatureFlagService.class);
        auditService = mock(FeatureFlagAuditService.class);
        navigationService = new PolicyEvaluationService(featureFlagService, auditService);
    }

    @Test
    void navigationAllowedWhenFeatureFlagEnabled() {
        PolicyRule rule = new PolicyRule(
                "nav-beta", "Beta Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.beta.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.beta.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertTrue(decision.matchedFeatureFlags().get("nav.beta.enabled"));
    }

    @Test
    void navigationDeniedWhenFeatureFlagDisabled() {
        PolicyRule rule = new PolicyRule(
                "nav-beta-deny", "Beta Nav Deny", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.beta.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.beta.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertFalse(decision.matchedFeatureFlags().get("nav.beta.enabled"));
    }

    @Test
    void navigationDisabledByFeatureFlagAuditEvent() {
        PolicyRule rule = new PolicyRule(
                "nav-gamma", "Gamma Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.gamma.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.gamma.enabled", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        navigationService.evaluate(context);

        verify(auditService).auditEvaluated(
                argThat(d -> "nav.gamma.enabled".equals(d.flagKey()) && !d.enabled()),
                eq("user-1"));
    }

    @Test
    void navigationWithMultipleRoutes() {
        PolicyRule rule1 = new PolicyRule(
                "nav-route-1", "Route 1", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.route1.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        PolicyRule rule2 = new PolicyRule(
                "nav-route-2", "Route 2", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.route2.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                10, "ACTIVE");
        navigationService.addRule(rule1);
        navigationService.addRule(rule2);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0, FeatureFlagEvaluationRequest.class);
                    boolean enabled = "nav.route1.enabled".equals(req.flagKey());
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision(req.flagKey(), enabled,
                                    enabled ? "enabled" : "disabled",
                                    enabled ? "EVALUATED" : "FLAG_DISABLED",
                                    FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", "ws-1", "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("nav-route-1", decision.matchedRuleId());
    }

    @Test
    void navigationWithNotEqualsOperator() {
        PolicyRule rule = new PolicyRule(
                "nav-ne", "NE Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.blocked\",\"operator\":\"ne\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.blocked", false, "disabled",
                                "FLAG_DISABLED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void navigationWithNoFeatureFlagCondition() {
        PolicyRule rule = new PolicyRule(
                "nav-tenant", "Tenant Nav", PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void navigationDefaultDenyWithNoRules() {
        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-default-deny", decision.matchedRuleId());
    }

    @Test
    void navigationDecisionIncludesAllEvaluatedFlags() {
        PolicyRule rule = new PolicyRule(
                "nav-include", "Include Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.include.flag\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.include.flag", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertNotNull(decision.matchedFeatureFlags());
        assertEquals(1, decision.matchedFeatureFlags().size());
        assertTrue(decision.matchedFeatureFlags().containsKey("nav.include.flag"));
    }

    @Test
    void navigationWithMobileRequestSource() {
        PolicyRule rule = new PolicyRule(
                "nav-mobile", "Mobile Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"nav.mobile.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenReturn(new FeatureFlagEvaluationResult(
                        new FeatureFlagDecision("nav.mobile.enabled", true, "enabled",
                                "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                "tenant-1", "ws-1", "user-1", Instant.now(), Map.of())));

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "mobile", Map.of());
        PolicyDecision decision = navigationService.evaluate(context);

        assertEquals(PolicyEffect.ALLOW, decision.effect());
    }

    @Test
    void navigationWithFeatureFlagPrefixStripped() {
        PolicyRule rule = new PolicyRule(
                "nav-prefix", "Prefix Nav", PolicyEffect.ALLOW,
                "{\"featureFlag\":{\"flagKey\":\"featureFlag.nav.prefix\",\"operator\":\"eq\",\"expectedValue\":true}}",
                5, "ACTIVE");
        navigationService.addRule(rule);

        when(featureFlagService.evaluate(any(FeatureFlagEvaluationRequest.class)))
                .thenAnswer(invocation -> {
                    FeatureFlagEvaluationRequest req = invocation.getArgument(0, FeatureFlagEvaluationRequest.class);
                    assertEquals("nav.prefix", req.flagKey());
                    return new FeatureFlagEvaluationResult(
                            new FeatureFlagDecision("nav.prefix", true, "enabled",
                                    "EVALUATED", FeatureFlagProviderType.LOCAL, null,
                                    "tenant-1", "ws-1", "user-1", Instant.now(), Map.of()));
                });

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "navigation", "web", Map.of());
        navigationService.evaluate(context);

        verify(featureFlagService).evaluate(argThat((FeatureFlagEvaluationRequest req) ->
                "nav.prefix".equals(req.flagKey())));
    }
}
