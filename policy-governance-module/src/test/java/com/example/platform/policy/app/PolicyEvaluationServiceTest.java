package com.example.platform.policy.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.policy.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PolicyEvaluationServiceTest {

    private PolicyEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyEvaluationService();
    }

    @Test
    void evaluateReturnsDefaultDenyWhenNoRulesMatch() {
        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertNotNull(decision);
        assertEquals(PolicyEffect.DENY, decision.effect());
    }

    @Test
    void addRuleAndEvaluateMatches() {
        PolicyRule allowRule = new PolicyRule(
                "rule-allow-test", "Test Allow",
                PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                10, "ACTIVE");
        service.addRule(allowRule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.ALLOW, decision.effect());
        assertEquals("rule-allow-test", decision.matchedRuleId());
    }

    @Test
    void denyRuleTakesPrecedenceByPriority() {
        PolicyRule allowRule = new PolicyRule(
                "rule-allow", "Allow",
                PolicyEffect.ALLOW,
                "{\"tenantId\":\"tenant-1\"}",
                20, "ACTIVE");
        PolicyRule denyRule = new PolicyRule(
                "rule-deny", "Deny",
                PolicyEffect.DENY,
                "{\"tenantId\":\"tenant-1\"}",
                10, "ACTIVE");
        service.addRule(allowRule);
        service.addRule(denyRule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.DENY, decision.effect());
        assertEquals("rule-deny", decision.matchedRuleId());
    }

    @Test
    void inactiveRuleDoesNotMatch() {
        PolicyRule inactiveRule = new PolicyRule(
                "rule-inactive", "Inactive",
                PolicyEffect.ALLOW,
                "{}",
                5, "INACTIVE");
        service.addRule(inactiveRule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.DENY, decision.effect());
        assertNotEquals("rule-inactive", decision.matchedRuleId());
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
    void listRulesReturnsAllRules() {
        List<PolicyRule> rules = service.listRules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> "rule-default-deny".equals(r.id())));
    }

    @Test
    void requireReviewEffect() {
        PolicyRule reviewRule = new PolicyRule(
                "rule-review", "Review",
                PolicyEffect.REQUIRE_REVIEW,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        service.addRule(reviewRule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.REQUIRE_REVIEW, decision.effect());
    }

    @Test
    void warnEffect() {
        PolicyRule warnRule = new PolicyRule(
                "rule-warn", "Warn",
                PolicyEffect.WARN,
                "{\"tenantId\":\"tenant-1\"}",
                5, "ACTIVE");
        service.addRule(warnRule);

        PolicyContext context = new PolicyContext(
                "user-1", "ADMIN", "tenant-1", "ws-1",
                "feature", "api", Map.of());

        PolicyDecision decision = service.evaluate(context);
        assertEquals(PolicyEffect.WARN, decision.effect());
    }
}
