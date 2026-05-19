package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalFeatureFlagProviderTest {

    private LocalFeatureFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalFeatureFlagProvider();
    }

    @Test
    void getFlagReturnsEmptyForUnknownFlag() {
        Optional<FeatureFlagDefinition> result = provider.getFlag("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void saveAndGetFlag() {
        FeatureFlagDefinition def = buildFlag("test-flag", true);
        provider.saveFlag(def);
        Optional<FeatureFlagDefinition> result = provider.getFlag("test-flag");
        assertTrue(result.isPresent());
        assertEquals("test-flag", result.get().flagKey());
    }

    @Test
    void listFlagsReturnsAllFlags() {
        provider.saveFlag(buildFlag("flag-1", true));
        provider.saveFlag(buildFlag("flag-2", false));
        assertEquals(2, provider.listFlags().size());
    }

    @Test
    void listFlagsByTagFiltersCorrectly() {
        FeatureFlagDefinition f1 = new FeatureFlagDefinition(
                "f1", "Flag 1", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of("beta"),
                Instant.now(), Instant.now(), false
        );
        FeatureFlagDefinition f2 = new FeatureFlagDefinition(
                "f2", "Flag 2", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of("stable"),
                Instant.now(), Instant.now(), false
        );
        provider.saveFlag(f1);
        provider.saveFlag(f2);
        List<FeatureFlagDefinition> betaFlags = provider.listFlagsByTag("beta");
        assertEquals(1, betaFlags.size());
        assertEquals("f1", betaFlags.get(0).flagKey());
    }

    @Test
    void deleteFlagRemovesFlag() {
        provider.saveFlag(buildFlag("to-delete", true));
        assertTrue(provider.deleteFlag("to-delete"));
        assertTrue(provider.getFlag("to-delete").isEmpty());
    }

    @Test
    void deleteFlagReturnsFalseForUnknown() {
        assertFalse(provider.deleteFlag("nonexistent"));
    }

    @Test
    void evaluateReturnsDefaultWhenFlagNotFound() {
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "missing", null, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("FLAG_NOT_DEFINED", decision.reasonCode());
        assertEquals(FeatureFlagProviderType.LOCAL, decision.providerType());
    }

    @Test
    void evaluateReturnsDefaultWhenFlagDisabled() {
        provider.saveFlag(buildFlag("disabled-flag", false));
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "disabled-flag", null, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("FLAG_DISABLED", decision.reasonCode());
    }

    @Test
    void evaluateReturnsDefaultWhenFlagArchived() {
        FeatureFlagDefinition archived = new FeatureFlagDefinition(
                "archived-flag", "Archived", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), true
        );
        provider.saveFlag(archived);
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "archived-flag", null, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("FLAG_ARCHIVED", decision.reasonCode());
    }

    @Test
    void evaluateReturnsDefaultWhenNoRulesMatch() {
        provider.saveFlag(buildFlag("no-rules", true));
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "no-rules", context, true);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertTrue(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void evaluateMatchesTenantRule() {
        provider.saveFlag(buildFlag("tenant-flag", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "tenant-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("tenant-flag", rule);
        FeatureFlagContext matchingContext = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "tenant-flag", matchingContext, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
        assertEquals("rule-1", decision.matchedRule());
    }

    @Test
    void evaluateDoesNotMatchWrongTenant() {
        provider.saveFlag(buildFlag("tenant-flag-2", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "tenant-flag-2", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("tenant-flag-2", rule);
        FeatureFlagContext nonMatchingContext = new FeatureFlagContext(
                "tenant-2", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "tenant-flag-2", nonMatchingContext, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void evaluateMatchesRoleRule() {
        provider.saveFlag(buildFlag("role-flag", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-role", "role-flag", 10, true,
                null, null, null, "ADMIN", null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("role-flag", rule);
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of("ADMIN"), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "role-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void evaluateDoesNotMatchWrongRole() {
        provider.saveFlag(buildFlag("role-flag-2", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-role", "role-flag-2", 10, true,
                null, null, null, "ADMIN", null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("role-flag-2", rule);
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of("USER"), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "role-flag-2", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
    }

    @Test
    void evaluateTierRule() {
        provider.saveFlag(buildFlag("tier-flag", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-tier", "tier-flag", 10, true,
                null, null, null, null, null, "enterprise",
                null, null, null, null, null, null
        );
        provider.saveRule("tier-flag", rule);
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                "enterprise", null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "tier-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void evaluateIgnoresDisabledRules() {
        provider.saveFlag(buildFlag("disabled-rule-flag", true));
        FeatureFlagTargetingRule disabledRule = new FeatureFlagTargetingRule(
                "rule-disabled", "disabled-rule-flag", 10, false,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("disabled-rule-flag", disabledRule);
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "disabled-rule-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void evaluateIgnoiredExpiredRules() {
        provider.saveFlag(buildFlag("expired-flag", true));
        FeatureFlagTargetingRule expiredRule = new FeatureFlagTargetingRule(
                "rule-expired", "expired-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null,
                Instant.now().minusSeconds(200), Instant.now().minusSeconds(100)
        );
        provider.saveRule("expired-flag", expiredRule);
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "expired-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void evaluateIgnoresNotYetStartedRules() {
        provider.saveFlag(buildFlag("future-flag", true));
        FeatureFlagTargetingRule futureRule = new FeatureFlagTargetingRule(
                "rule-future", "future-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null,
                Instant.now().plusSeconds(100), Instant.now().plusSeconds(200)
        );
        provider.saveRule("future-flag", futureRule);
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "future-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }

    @Test
    void evaluatePriorityOrdering() {
        provider.saveFlag(buildFlag("priority-flag", true));
        FeatureFlagTargetingRule lowPriority = new FeatureFlagTargetingRule(
                "rule-low", "priority-flag", 100, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        FeatureFlagTargetingRule highPriority = new FeatureFlagTargetingRule(
                "rule-high", "priority-flag", 1, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        provider.saveRule("priority-flag", lowPriority);
        provider.saveRule("priority-flag", highPriority);
        FeatureFlagContext context = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "priority-flag", context, false);
        FeatureFlagDecision decision = provider.evaluate(request);
        assertTrue(decision.enabled());
        assertEquals("rule-high", decision.matchedRule());
    }

    @Test
    void evaluateBatchProcessesAllRequests() {
        provider.saveFlag(buildFlag("batch-1", true));
        provider.saveFlag(buildFlag("batch-2", true));
        List<FeatureFlagEvaluationRequest> requests = List.of(
                new FeatureFlagEvaluationRequest("batch-1", null, true),
                new FeatureFlagEvaluationRequest("batch-2", null, false),
                new FeatureFlagEvaluationRequest("batch-missing", null, false)
        );
        List<FeatureFlagDecision> decisions = provider.evaluateBatch(requests);
        assertEquals(3, decisions.size());
        assertTrue(decisions.get(0).enabled());
        assertFalse(decisions.get(1).enabled());
        assertFalse(decisions.get(2).enabled());
    }

    @Test
    void getRulesReturnsCorrectRules() {
        provider.saveFlag(buildFlag("rules-flag", true));
        provider.saveRule("rules-flag", new FeatureFlagTargetingRule(
                "r1", "rules-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));
        provider.saveRule("rules-flag", new FeatureFlagTargetingRule(
                "r2", "rules-flag", 20, true,
                "tenant-2", null, null, null, null, null,
                null, null, null, null, null, null
        ));
        List<FeatureFlagTargetingRule> rules = provider.getRules("rules-flag");
        assertEquals(2, rules.size());
    }

    @Test
    void clearRulesRemovesAllRules() {
        provider.saveFlag(buildFlag("clear-flag", true));
        provider.saveRule("clear-flag", new FeatureFlagTargetingRule(
                "r1", "clear-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));
        provider.clearRules("clear-flag");
        assertTrue(provider.getRules("clear-flag").isEmpty());
    }

    @Test
    void percentageRolloutDeterministic() {
        provider.saveFlag(buildFlag("pct-flag", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-pct", "pct-flag", 10, true,
                null, null, null, null, null, null,
                50.0, null, null, null, null, null
        );
        provider.saveRule("pct-flag", rule);
        int enabled = 0;
        int total = 100;
        for (int i = 0; i < total; i++) {
            FeatureFlagContext ctx = new FeatureFlagContext(
                    null, null, "user-" + i, List.of(), List.of(),
                    null, null, null, null, null, Map.of());
            FeatureFlagDecision decision = provider.evaluate(
                    new FeatureFlagEvaluationRequest("pct-flag", ctx, false));
            if (decision.enabled()) enabled++;
        }
        assertTrue(enabled > 0);
        assertTrue(enabled < total);
    }

    private FeatureFlagDefinition buildFlag(String flagKey, boolean enabled) {
        return new FeatureFlagDefinition(
                flagKey, flagKey, null, FeatureFlagType.BOOLEAN, enabled,
                List.of(), List.of(), enabled, "test-owner", List.of("test"),
                Instant.now(), Instant.now(), false
        );
    }
}
