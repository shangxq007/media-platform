package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.FeatureFlagAuditService.FeatureFlagAuditEvent;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagAuditTest {

    private FeatureFlagAuditService auditService;
    private TestAuditPort testAuditPort;

    @BeforeEach
    void setUp() {
        testAuditPort = new TestAuditPort();
        auditService = new FeatureFlagAuditService(testAuditPort);
    }

    @Test
    void auditFlagCreatedWritesToPort() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "audit-flag", "Audit Flag", "desc", FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        auditService.auditFlagCreated(def, "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("admin-1", testAuditPort.getRecords().get(0).actorType());
        assertEquals("FLAG_CREATED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditFlagUpdatedWritesToPort() {
        FeatureFlagDefinition before = new FeatureFlagDefinition(
                "flag-1", "Old", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        FeatureFlagDefinition after = new FeatureFlagDefinition(
                "flag-1", "New", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), false, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        auditService.auditFlagUpdated("flag-1", before, after, "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_UPDATED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditFlagEnabledWritesToPort() {
        auditService.auditFlagEnabled("flag-1", "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_ENABLED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditFlagDisabledWritesToPort() {
        auditService.auditFlagDisabled("flag-1", "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_DISABLED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditFlagArchivedWritesToPort() {
        auditService.auditFlagArchived("flag-1", "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_ARCHIVED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditRuleCreatedWritesToPort() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "flag-1", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        auditService.auditRuleCreated("flag-1", rule, "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("RULE_CREATED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditRuleUpdatedWritesToPort() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "flag-1", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        auditService.auditRuleUpdated("flag-1", rule, "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("RULE_UPDATED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditRuleDeletedWritesToPort() {
        auditService.auditRuleDeleted("flag-1", "rule-1", "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("RULE_DELETED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditEvaluatedWritesToPort() {
        FeatureFlagDecision decision = new FeatureFlagDecision(
                "flag-1", true, "enabled", "EVALUATED",
                FeatureFlagProviderType.LOCAL, null,
                null, null, null, Instant.now(), Map.of()
        );
        auditService.auditEvaluated(decision, "user-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_EVALUATED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditEvaluationFailedWritesToPort() {
        auditService.auditEvaluationFailed("flag-1", "ERR-001", "Something failed", "user-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("FLAG_EVALUATION_FAILED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditRolloutChangedWritesToPort() {
        auditService.auditRolloutChanged("flag-1", 10.0, 50.0, "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("ROLLOUT_CHANGED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditVariantChangedWritesToPort() {
        auditService.auditVariantChanged("flag-1", "v1", "v2", "admin-1");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("VARIANT_CHANGED", testAuditPort.getRecords().get(0).action());
    }

    @Test
    void auditPolicyEvaluatedWithFeatureFlagWritesToPort() {
        auditService.auditPolicyEvaluatedWithFeatureFlag(
                "flag-1", true, "admin-1",
                "tenant-1", "ws-1", "user-1",
                "rule-1", "enabled", "POLICY_MATCHED",
                "trace-123", "api");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("POLICY_EVALUATED_WITH_FEATURE_FLAG",
                testAuditPort.getRecords().get(0).action());
        Map<String, Object> payload = testAuditPort.getRecords().get(0).payload();
        assertEquals("flag-1", payload.get("flagKey"));
        assertEquals("tenant-1", payload.get("tenantId"));
        assertEquals("ws-1", payload.get("workspaceId"));
        assertEquals("user-1", payload.get("userId"));
        assertEquals("admin-1", payload.get("actorId"));
        assertEquals("rule-1", payload.get("matchedRule"));
        assertEquals("enabled", payload.get("variant"));
        assertEquals("POLICY_MATCHED", payload.get("reason"));
        assertEquals("trace-123", payload.get("traceId"));
        assertEquals("api", payload.get("requestSource"));
        assertEquals(true, payload.get("flagEnabled"));
    }

    @Test
    void auditAccessDeniedByFeatureFlagWritesToPort() {
        auditService.auditAccessDeniedByFeatureFlag(
                "flag-1", "system",
                "tenant-1", "ws-1", "user-1",
                "rule-1", "disabled", "FLAG_DISABLED",
                "trace-456", "web");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("ACCESS_DENIED_BY_FEATURE_FLAG",
                testAuditPort.getRecords().get(0).action());
        Map<String, Object> payload = testAuditPort.getRecords().get(0).payload();
        assertEquals("flag-1", payload.get("flagKey"));
        assertEquals("tenant-1", payload.get("tenantId"));
        assertEquals("FLAG_DISABLED", payload.get("reason"));
    }

    @Test
    void auditNavigationDisabledByFeatureFlagWritesToPort() {
        auditService.auditNavigationDisabledByFeatureFlag(
                "flag-1", "system",
                "tenant-1", "ws-1", "user-1",
                null, "disabled", "FLAG_NOT_DEFINED",
                "trace-789", "mobile");
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("NAVIGATION_DISABLED_BY_FEATURE_FLAG",
                testAuditPort.getRecords().get(0).action());
        Map<String, Object> payload = testAuditPort.getRecords().get(0).payload();
        assertEquals("flag-1", payload.get("flagKey"));
        assertEquals("mobile", payload.get("requestSource"));
    }

    @Test
    void allAuditEventTypesAreCovered() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "all-types", "All Types", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "r1", "all-types", 10, true,
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );
        FeatureFlagDecision decision = new FeatureFlagDecision(
                "all-types", true, "enabled", "EVALUATED",
                FeatureFlagProviderType.LOCAL, null,
                null, null, null, Instant.now(), Map.of()
        );

        auditService.auditFlagCreated(def, "a");
        auditService.auditFlagEnabled("all-types", "a");
        auditService.auditFlagDisabled("all-types", "a");
        auditService.auditFlagArchived("all-types", "a");
        auditService.auditRuleCreated("all-types", rule, "a");
        auditService.auditRuleUpdated("all-types", rule, "a");
        auditService.auditRuleDeleted("all-types", "r1", "a");
        auditService.auditEvaluated(decision, "a");
        auditService.auditEvaluationFailed("all-types", "ERR", "msg", "a");
        auditService.auditRolloutChanged("all-types", 0.0, 50.0, "a");
        auditService.auditVariantChanged("all-types", "v1", "v2", "a");
        auditService.auditPolicyEvaluatedWithFeatureFlag("all-types", true, "a",
                "t", "w", "u", "r", "v", "reason", "trace", "src");
        auditService.auditAccessDeniedByFeatureFlag("all-types", "a",
                "t", "w", "u", "r", "v", "reason", "trace", "src");
        auditService.auditNavigationDisabledByFeatureFlag("all-types", "a",
                "t", "w", "u", "r", "v", "reason", "trace", "src");

        List<String> actions = testAuditPort.getRecords().stream()
                .map(TestAuditRecord::action).toList();

        assertTrue(actions.contains("FLAG_CREATED"));
        assertTrue(actions.contains("FLAG_ENABLED"));
        assertTrue(actions.contains("FLAG_DISABLED"));
        assertTrue(actions.contains("FLAG_ARCHIVED"));
        assertTrue(actions.contains("RULE_CREATED"));
        assertTrue(actions.contains("RULE_UPDATED"));
        assertTrue(actions.contains("RULE_DELETED"));
        assertTrue(actions.contains("FLAG_EVALUATED"));
        assertTrue(actions.contains("FLAG_EVALUATION_FAILED"));
        assertTrue(actions.contains("ROLLOUT_CHANGED"));
        assertTrue(actions.contains("VARIANT_CHANGED"));
        assertTrue(actions.contains("POLICY_EVALUATED_WITH_FEATURE_FLAG"));
        assertTrue(actions.contains("ACCESS_DENIED_BY_FEATURE_FLAG"));
        assertTrue(actions.contains("NAVIGATION_DISABLED_BY_FEATURE_FLAG"));
    }

    @Test
    void getRecentEventsReturnsAllInMemory() {
        auditService.auditFlagEnabled("f1", "a");
        auditService.auditFlagEnabled("f2", "a");
        auditService.auditFlagEnabled("f3", "a");
        List<FeatureFlagAuditEvent> recent = auditService.getRecentEvents(10);
        assertEquals(3, recent.size());
    }

    @Test
    void getEventsByFlagFiltersInMemory() {
        auditService.auditFlagEnabled("flag-a", "a");
        auditService.auditFlagEnabled("flag-b", "a");
        assertEquals(1, auditService.getEventsByFlag("flag-a").size());
        assertEquals(1, auditService.getEventsByFlag("flag-b").size());
    }

    @Test
    void auditWithNullActorUsesSystem() {
        FeatureFlagAuditService serviceWithPort = new FeatureFlagAuditService(testAuditPort);
        serviceWithPort.auditFlagEnabled("flag-null-actor", null);
        assertEquals(1, testAuditPort.getRecords().size());
        assertEquals("system", testAuditPort.getRecords().get(0).actorType());
    }

    @Test
    void auditWithNullPortDoesNotThrow() {
        FeatureFlagAuditService serviceWithNullPort = new FeatureFlagAuditService(null);
        assertDoesNotThrow(() -> serviceWithNullPort.auditFlagEnabled("flag-1", "admin"));
        List<FeatureFlagAuditEvent> events = serviceWithNullPort.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
    }

    private record TestAuditRecord(
            String actorType, String action, String category,
            String resourceType, String resourceId, Map<String, Object> payload) {}

    private static class TestAuditPort implements AuditPort {
        private final List<TestAuditRecord> records = new ArrayList<>();

        @Override
        public void record(String actorType, String action, String category,
                           String resourceType, String resourceId, Map<String, Object> payload) {
            records.add(new TestAuditRecord(actorType, action, category, resourceType, resourceId, payload));
        }

        List<TestAuditRecord> getRecords() {
            return List.copyOf(records);
        }
    }
}
