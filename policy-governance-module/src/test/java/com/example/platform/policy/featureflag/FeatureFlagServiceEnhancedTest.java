package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.AppFeaturesProperties;
import com.example.platform.policy.featureflag.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagServiceEnhancedTest {

    private FeatureFlagService service;
    private LocalFeatureFlagProvider localProvider;

    @BeforeEach
    void setUp() {
        localProvider = new LocalFeatureFlagProvider();
        AppFeaturesProperties props = new AppFeaturesProperties();
        OpenFeatureFlagEvaluator openFeatureEvaluator = new OpenFeatureFlagEvaluator();
        service = new FeatureFlagService(localProvider, openFeatureEvaluator, props);
    }

    @Test
    void createFlagPersistsAndReturns() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "new-flag", "New Flag", "desc", FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of("test"),
                null, null, false
        );
        FeatureFlagDefinition created = service.createFlag(def);
        assertNotNull(created.createdAt());
        assertNotNull(created.updatedAt());
        assertEquals("new-flag", created.flagKey());
    }

    @Test
    void getFlagReturnsEmptyForUnknown() {
        Optional<FeatureFlagDefinition> result = service.getFlag("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void getFlagReturnsCreatedFlag() {
        service.createFlag(buildFlag("findable", true));
        Optional<FeatureFlagDefinition> result = service.getFlag("findable");
        assertTrue(result.isPresent());
        assertEquals("findable", result.get().flagKey());
    }

    @Test
    void listFlagsReturnsAllCreated() {
        service.createFlag(buildFlag("f1", true));
        service.createFlag(buildFlag("f2", false));
        assertEquals(2, service.listFlags().size());
    }

    @Test
    void updateFlagModifiesExisting() {
        service.createFlag(buildFlag("update-me", true));
        FeatureFlagDefinition updateDef = new FeatureFlagDefinition(
                "update-me", "Updated Name", "updated", FeatureFlagType.BOOLEAN, false,
                List.of(), List.of(), false, "owner", List.of("updated"),
                null, null, false
        );
        FeatureFlagDefinition updated = service.updateFlag("update-me", updateDef);
        assertEquals("Updated Name", updated.name());
        assertFalse(updated.enabled());
    }

    @Test
    void updateFlagThrowsForUnknown() {
        FeatureFlagDefinition def = buildFlag("unknown", true);
        assertThrows(IllegalArgumentException.class, () -> service.updateFlag("unknown", def));
    }

    @Test
    void enableFlagSetsEnabledTrue() {
        service.createFlag(buildFlag("toggle", false));
        FeatureFlagDefinition enabled = service.enableFlag("toggle");
        assertTrue(enabled.enabled());
    }

    @Test
    void disableFlagSetsEnabledFalse() {
        service.createFlag(buildFlag("toggle", true));
        FeatureFlagDefinition disabled = service.disableFlag("toggle");
        assertFalse(disabled.enabled());
    }

    @Test
    void archiveFlagSetsArchivedTrue() {
        service.createFlag(buildFlag("to-archive", true));
        FeatureFlagDefinition archived = service.archiveFlag("to-archive");
        assertTrue(archived.archived());
        assertFalse(archived.enabled());
    }

    @Test
    void enableFlagThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> service.enableFlag("nonexistent"));
    }

    @Test
    void disableFlagThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> service.disableFlag("nonexistent"));
    }

    @Test
    void archiveFlagThrowsForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> service.archiveFlag("nonexistent"));
    }

    @Test
    void addTargetingRulePersists() {
        service.createFlag(buildFlag("rule-flag", true));
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "rule-flag", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        service.addTargetingRule("rule-flag", rule);
        List<FeatureFlagTargetingRule> rules = service.getTargetingRules("rule-flag");
        assertEquals(1, rules.size());
        assertEquals("rule-1", rules.get(0).ruleId());
    }

    @Test
    void addTargetingRulesViaCreateFlag() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-via-create", "flag-with-rules", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "flag-with-rules", "Flag", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(rule), true, "owner", List.of(),
                null, null, false
        );
        service.createFlag(def);
        List<FeatureFlagTargetingRule> rules = service.getTargetingRules("flag-with-rules");
        assertEquals(1, rules.size());
    }

    @Test
    void getFlagsForContextReturnsNonArchived() {
        service.createFlag(buildFlag("active-1", true));
        service.createFlag(buildFlag("active-2", true));
        service.createFlag(buildFlag("archived", true));
        service.archiveFlag("archived");
        FeatureFlagContext context = new FeatureFlagContext(
                null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        List<FeatureFlagDefinition> flags = service.getFlagsForContext(context);
        assertTrue(flags.stream().noneMatch(FeatureFlagDefinition::archived));
    }

    @Test
    void evaluateReturnsResult() {
        service.createFlag(buildFlag("eval-flag", true));
        FeatureFlagEvaluationRequest request = new FeatureFlagEvaluationRequest(
                "eval-flag", null, true);
        FeatureFlagEvaluationResult result = service.evaluate(request);
        assertNotNull(result);
        assertNotNull(result.decision());
        assertEquals("eval-flag", result.decision().flagKey());
    }

    @Test
    void evaluateBatchReturnsAllResults() {
        service.createFlag(buildFlag("batch-1", true));
        service.createFlag(buildFlag("batch-2", false));
        List<FeatureFlagEvaluationRequest> requests = List.of(
                new FeatureFlagEvaluationRequest("batch-1", null, true),
                new FeatureFlagEvaluationRequest("batch-2", null, false)
        );
        List<FeatureFlagEvaluationResult> results = service.evaluateBatch(requests);
        assertEquals(2, results.size());
    }

    @Test
    void isStillBackwardCompatibleWithInterface() {
        boolean result = service.isEnabled("any-flag", "user-1", Map.of(), true);
        assertTrue(result);
    }

    @Test
    void isBackwardCompatibleReturnsDefaultFalse() {
        boolean result = service.isEnabled("any-flag", "user-1", Map.of(), false);
        assertFalse(result);
    }

    private FeatureFlagDefinition buildFlag(String flagKey, boolean enabled) {
        return new FeatureFlagDefinition(
                flagKey, flagKey, null, FeatureFlagType.BOOLEAN, enabled,
                List.of(), List.of(), enabled, "test-owner", List.of("test"),
                Instant.now(), Instant.now(), false
        );
    }
}
