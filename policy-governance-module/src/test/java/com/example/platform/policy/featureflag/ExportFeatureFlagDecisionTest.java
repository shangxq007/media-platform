package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExportFeatureFlagDecisionTest {

    private LocalFeatureFlagProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalFeatureFlagProvider();
    }

    @Test
    void exportAllowedWhenFeatureFlagEnabled() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.pdf.enabled", "PDF Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.pdf.enabled", new FeatureFlagTargetingRule(
                "r-pdf", "export.pdf.enabled", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.pdf.enabled", ctx, false));

        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
        assertEquals("tenant-1", decision.tenantId());
        assertEquals("user-1", decision.userId());
    }

    @Test
    void exportDeniedWhenFeatureFlagDisabled() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.4k.enabled", "4K Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), false, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));

        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.4k.enabled", null, false));

        assertFalse(decision.enabled());
        assertEquals("FLAG_DISABLED", decision.reasonCode());
    }

    @Test
    void exportDeniedWhenFeatureFlagArchived() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.old.enabled", "Old Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), true
        ));

        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.old.enabled", null, false));

        assertFalse(decision.enabled());
        assertEquals("FLAG_ARCHIVED", decision.reasonCode());
    }

    @Test
    void exportDeniedWhenFeatureFlagNotDefined() {
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.unknown.enabled", null, false));

        assertFalse(decision.enabled());
        assertEquals("FLAG_NOT_DEFINED", decision.reasonCode());
    }

    @Test
    void exportDecisionContainsAllRequiredFields() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.test.enabled", "Test Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.test.enabled", new FeatureFlagTargetingRule(
                "r-test", "export.test.enabled", 10, true,
                "tenant-1", "ws-1", "user-1", null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", "ws-1", "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.test.enabled", ctx, false));

        assertNotNull(decision.flagKey());
        assertTrue(decision.enabled());
        assertNotNull(decision.variant());
        assertNotNull(decision.reasonCode());
        assertNotNull(decision.providerType());
        assertNotNull(decision.evaluatedAt());
        assertNotNull(decision.details());
        assertEquals("tenant-1", decision.tenantId());
        assertEquals("ws-1", decision.workspaceId());
        assertEquals("user-1", decision.userId());
    }

    @Test
    void exportDecisionWithVariant() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.variant.enabled", "Variant Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(new FeatureFlagVariant("enabled", "on", "Enabled variant")),
                List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.variant.enabled", new FeatureFlagTargetingRule(
                "r-var", "export.variant.enabled", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.variant.enabled", ctx, false));

        assertTrue(decision.enabled());
        assertEquals("enabled", decision.variant());
    }

    @Test
    void exportDecisionMatchedRuleField() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.rule.enabled", "Rule Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.rule.enabled", new FeatureFlagTargetingRule(
                "r-match", "export.rule.enabled", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.rule.enabled", ctx, false));

        assertEquals("r-match", decision.matchedRule());
        assertTrue(decision.details().containsKey("matchedRule"));
    }

    @Test
    void exportDecisionWhenNoRulesMatch() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.norules.enabled", "No Rules Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.norules.enabled", ctx, true));

        assertTrue(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
        assertNull(decision.matchedRule());
    }

    @Test
    void exportDecisionWithPercentageRollout() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.pct.enabled", "Pct Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.pct.enabled", new FeatureFlagTargetingRule(
                "r-pct", "export.pct.enabled", 10, true,
                null, null, null, null, null, null,
                100.0, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                null, null, "user-1", List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.pct.enabled", ctx, false));

        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void exportDecisionProviderTypeIsLocal() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.provider.enabled", "Provider Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.provider.enabled", new FeatureFlagTargetingRule(
                "r-prov", "export.provider.enabled", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.provider.enabled", ctx, false));

        assertEquals(FeatureFlagProviderType.LOCAL, decision.providerType());
    }

    @Test
    void exportDecisionWithTierRestriction() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.tier.enabled", "Tier Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.tier.enabled", new FeatureFlagTargetingRule(
                "r-tier", "export.tier.enabled", 10, true,
                null, null, null, null, null, "enterprise",
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                "enterprise", null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.tier.enabled", ctx, false));

        assertTrue(decision.enabled());
        assertEquals("RULE_MATCHED", decision.reasonCode());
    }

    @Test
    void exportDecisionWithTierRestrictionDenied() {
        provider.saveFlag(new FeatureFlagDefinition(
                "export.tier-deny.enabled", "Tier Deny Export", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        ));
        provider.saveRule("export.tier-deny.enabled", new FeatureFlagTargetingRule(
                "r-tier-deny", "export.tier-deny.enabled", 10, true,
                null, null, null, null, null, "enterprise",
                null, null, null, null, null, null
        ));

        FeatureFlagContext ctx = new FeatureFlagContext(
                "tenant-1", null, null, List.of(), List.of(),
                "free", null, null, null, null, Map.of());
        FeatureFlagDecision decision = provider.evaluate(
                new FeatureFlagEvaluationRequest("export.tier-deny.enabled", ctx, false));

        assertFalse(decision.enabled());
        assertEquals("NO_MATCHING_RULE", decision.reasonCode());
    }
}
