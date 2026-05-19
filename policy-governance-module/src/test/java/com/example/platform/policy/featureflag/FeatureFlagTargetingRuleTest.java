package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagTargetingRuleTest {

    @Test
    void ruleRecordStoresAllFields() {
        Instant now = Instant.now();
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "flag-1", 10, true,
                "tenant-1", "ws-1", "user-1", "ADMIN", "group-1",
                "enterprise", 50.0, "us-east", "api", "prod",
                now, now.plusSeconds(3600)
        );
        assertEquals("rule-1", rule.ruleId());
        assertEquals("flag-1", rule.flagKey());
        assertEquals(10, rule.priority());
        assertTrue(rule.enabled());
        assertEquals("tenant-1", rule.tenantId());
        assertEquals("ws-1", rule.workspaceId());
        assertEquals("user-1", rule.userId());
        assertEquals("ADMIN", rule.role());
        assertEquals("group-1", rule.group());
        assertEquals("enterprise", rule.tier());
        assertEquals(50.0, rule.percentage());
        assertEquals("us-east", rule.region());
        assertEquals("api", rule.requestSource());
        assertEquals("prod", rule.environment());
        assertEquals(now, rule.startAt());
        assertEquals(now.plusSeconds(3600), rule.endAt());
    }

    @Test
    void ruleWithMinimalFields() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-min", null, null, true,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null
        );
        assertEquals("rule-min", rule.ruleId());
        assertNull(rule.flagKey());
        assertNull(rule.priority());
        assertTrue(rule.enabled());
        assertNull(rule.tenantId());
        assertNull(rule.workspaceId());
        assertNull(rule.userId());
        assertNull(rule.role());
        assertNull(rule.group());
        assertNull(rule.tier());
        assertNull(rule.percentage());
        assertNull(rule.region());
        assertNull(rule.requestSource());
        assertNull(rule.environment());
        assertNull(rule.startAt());
        assertNull(rule.endAt());
    }

    @Test
    void ruleEqualityBasedOnRecordComponents() {
        FeatureFlagTargetingRule rule1 = new FeatureFlagTargetingRule(
                "r1", "f1", 10, true,
                "t1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        FeatureFlagTargetingRule rule2 = new FeatureFlagTargetingRule(
                "r1", "f1", 10, true,
                "t1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    void ruleInequality() {
        FeatureFlagTargetingRule rule1 = new FeatureFlagTargetingRule(
                "r1", "f1", 10, true,
                "t1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        FeatureFlagTargetingRule rule2 = new FeatureFlagTargetingRule(
                "r2", "f1", 10, true,
                "t1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        assertNotEquals(rule1, rule2);
    }

    @Test
    void ruleDisabledFlag() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-disabled", "f1", 10, false,
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );
        assertFalse(rule.enabled());
    }

    @Test
    void ruleWithPercentageOnly() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-pct", "f1", 10, true,
                null, null, null, null, null, null,
                75.0, null, null, null, null, null
        );
        assertEquals(75.0, rule.percentage());
        assertNull(rule.tenantId());
        assertNull(rule.userId());
    }

    @Test
    void ruleWithTimeBounds() {
        Instant start = Instant.now().plusSeconds(100);
        Instant end = Instant.now().plusSeconds(200);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-time", "f1", 10, true,
                null, null, null, null, null, null,
                null, null, null, null, start, end
        );
        assertEquals(start, rule.startAt());
        assertEquals(end, rule.endAt());
    }

    @Test
    void ruleToStringContainsKeyFields() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r1", "f1", 10, true,
                "t1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        String str = rule.toString();
        assertTrue(str.contains("r1"));
        assertTrue(str.contains("f1"));
    }

    @Test
    void ruleMatchesContextByTenantId() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "tenant-match", "Tenant Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-tenant", "tenant-match", 10, true,
                "acme", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("tenant-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                "acme", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("tenant-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByUserId() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "user-match", "User Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-user", "user-match", 10, true,
                null, null, "user-42", null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("user-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, "user-42", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("user-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByRole() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "role-match", "Role Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-role", "role-match", 10, true,
                null, null, null, "ADMIN", null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("role-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of("ADMIN", "USER"), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("role-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByGroup() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "group-match", "Group Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-group", "group-match", 10, true,
                null, null, null, null, "beta-testers", null,
                null, null, null, null, null, null
        );
        provider.saveRule("group-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of(), List.of("beta-testers"),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("group-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByTier() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "tier-match", "Tier Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-tier", "tier-match", 10, true,
                null, null, null, null, null, "premium",
                null, null, null, null, null, null
        );
        provider.saveRule("tier-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                "premium", null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("tier-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByRegion() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "region-match", "Region Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-region", "region-match", 10, true,
                null, null, null, null, null, null,
                null, "eu-west", null, null, null, null
        );
        provider.saveRule("region-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, "eu-west", null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("region-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByRequestSource() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "source-match", "Source Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-source", "source-match", 10, true,
                null, null, null, null, null, null,
                null, null, "mobile-app", null, null, null
        );
        provider.saveRule("source-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, "mobile-app", null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("source-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByEnvironment() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "env-match", "Env Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-env", "env-match", 10, true,
                null, null, null, null, null, null,
                null, null, null, "staging", null, null
        );
        provider.saveRule("env-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, "staging", null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("env-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleMatchesContextByWorkspaceId() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "ws-match", "WS Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-ws", "ws-match", 10, true,
                null, "workspace-42", null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("ws-match", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                null, "workspace-42", null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("ws-match", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleDoesNotMatchWrongTenant() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "no-match-tenant", "No Match", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-tenant", "no-match-tenant", 10, true,
                "acme", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("no-match-tenant", rule);

        FeatureFlagContext nonMatchingCtx = new FeatureFlagContext(
                "other-tenant", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("no-match-tenant", nonMatchingCtx, false));
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void ruleDoesNotMatchWrongWorkspace() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "no-match-ws", "No Match WS", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-ws", "no-match-ws", 10, true,
                null, "ws-1", null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("no-match-ws", rule);

        FeatureFlagContext nonMatchingCtx = new FeatureFlagContext(
                null, "ws-2", null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("no-match-ws", nonMatchingCtx, false));
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void ruleWithMultipleCriteriaMatchesAll() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "multi-criteria", "Multi", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-multi", "multi-criteria", 10, true,
                "acme", null, null, "ADMIN", null, "enterprise",
                null, null, null, null, null, null
        );
        provider.saveRule("multi-criteria", rule);

        FeatureFlagContext matchingCtx = new FeatureFlagContext(
                "acme", null, null, List.of("ADMIN"), List.of(),
                "enterprise", null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("multi-criteria", matchingCtx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void ruleWithMultipleCriteriaFailsOnPartialMatch() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "partial-match", "Partial", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-partial", "partial-match", 10, true,
                "acme", null, null, "ADMIN", null, "enterprise",
                null, null, null, null, null, null
        );
        provider.saveRule("partial-match", rule);

        FeatureFlagContext partialCtx = new FeatureFlagContext(
                "acme", null, null, List.of("USER"), List.of(),
                "enterprise", null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("partial-match", partialCtx, false));
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void ruleIgnoresNullContextFieldsForWildcardMatch() {
        LocalFeatureFlagProvider provider = new LocalFeatureFlagProvider();
        FeatureFlagDefinition flag = new FeatureFlagDefinition(
                "wildcard-flag", "Wildcard", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(flag);
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r-wildcard", "wildcard-flag", 10, true,
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("wildcard-flag", rule);

        FeatureFlagContext ctx = new FeatureFlagContext(
                "any-tenant", "any-ws", "any-user", List.of("ANY"), List.of("ANY"),
                "any-tier", "any-source", "any-env", "any-region", null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("wildcard-flag", ctx, false));
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }
}
