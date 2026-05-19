package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.FeatureFlagAuditService.FeatureFlagAuditEvent;
import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagAuditServiceTest {

    private FeatureFlagAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new FeatureFlagAuditService(new NoopAuditPort());
    }

    @Test
    void auditFlagCreatedRecordsEvent() {
        FeatureFlagDefinition def = new FeatureFlagDefinition(
                "test-flag", "Test", "desc", FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        auditService.auditFlagCreated(def, "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("test-flag");
        assertEquals(1, events.size());
        assertEquals("FLAG_CREATED", events.get(0).eventType());
        assertEquals("admin-1", events.get(0).actor());
    }

    @Test
    void auditFlagEnabledRecordsEvent() {
        auditService.auditFlagEnabled("flag-1", "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_ENABLED", events.get(0).eventType());
    }

    @Test
    void auditFlagDisabledRecordsEvent() {
        auditService.auditFlagDisabled("flag-1", "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_DISABLED", events.get(0).eventType());
    }

    @Test
    void auditFlagArchivedRecordsEvent() {
        auditService.auditFlagArchived("flag-1", "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_ARCHIVED", events.get(0).eventType());
    }

    @Test
    void auditRuleCreatedRecordsEvent() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "flag-1", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        auditService.auditRuleCreated("flag-1", rule, "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("RULE_CREATED", events.get(0).eventType());
    }

    @Test
    void auditRuleUpdatedRecordsEvent() {
        FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
                "rule-1", "flag-1", 10, true,
                "tenant-1", null, null, null, null, null,
                null, null, null, null, null, null
        );
        auditService.auditRuleUpdated("flag-1", rule, "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("RULE_UPDATED", events.get(0).eventType());
    }

    @Test
    void auditRuleDeletedRecordsEvent() {
        auditService.auditRuleDeleted("flag-1", "rule-1", "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("RULE_DELETED", events.get(0).eventType());
    }

    @Test
    void auditEvaluatedRecordsEvent() {
        FeatureFlagDecision decision = new FeatureFlagDecision(
                "flag-1", true, "enabled", "EVALUATED",
                FeatureFlagProviderType.LOCAL, null,
                null, null, null, Instant.now(), Map.of()
        );
        auditService.auditEvaluated(decision, "user-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_EVALUATED", events.get(0).eventType());
    }

    @Test
    void auditEvaluationFailedRecordsEvent() {
        auditService.auditEvaluationFailed("flag-1", "ERR-001", "Something failed", "user-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_EVALUATION_FAILED", events.get(0).eventType());
    }

    @Test
    void auditRolloutChangedRecordsEvent() {
        auditService.auditRolloutChanged("flag-1", 10.0, 50.0, "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("ROLLOUT_CHANGED", events.get(0).eventType());
        assertEquals(10.0, events.get(0).details().get("oldPercentage"));
        assertEquals(50.0, events.get(0).details().get("newPercentage"));
    }

    @Test
    void auditVariantChangedRecordsEvent() {
        auditService.auditVariantChanged("flag-1", "v1", "v2", "admin-1");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("VARIANT_CHANGED", events.get(0).eventType());
    }

    @Test
    void getRecentEventsReturnsLatestEvents() {
        auditService.auditFlagEnabled("flag-1", "admin");
        auditService.auditFlagDisabled("flag-1", "admin");
        auditService.auditFlagArchived("flag-1", "admin");
        List<FeatureFlagAuditEvent> recent = auditService.getRecentEvents(2);
        assertEquals(2, recent.size());
        assertEquals("FLAG_DISABLED", recent.get(0).eventType());
        assertEquals("FLAG_ARCHIVED", recent.get(1).eventType());
    }

    @Test
    void getEventsByFlagFiltersCorrectly() {
        auditService.auditFlagEnabled("flag-a", "admin");
        auditService.auditFlagEnabled("flag-b", "admin");
        assertEquals(1, auditService.getEventsByFlag("flag-a").size());
        assertEquals(1, auditService.getEventsByFlag("flag-b").size());
    }

    @Test
    void getEventsByFlagReturnsEmptyForUnknown() {
        assertTrue(auditService.getEventsByFlag("nonexistent").isEmpty());
    }

    @Test
    void auditFlagUpdatedDetectsChanges() {
        FeatureFlagDefinition before = new FeatureFlagDefinition(
                "flag-1", "Old Name", null, FeatureFlagType.BOOLEAN, true,
                List.of(), List.of(), true, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        FeatureFlagDefinition after = new FeatureFlagDefinition(
                "flag-1", "New Name", null, FeatureFlagType.STRING, true,
                List.of(), List.of(), false, "owner", List.of(),
                Instant.now(), Instant.now(), false
        );
        auditService.auditFlagUpdated("flag-1", before, after, "admin");
        List<FeatureFlagAuditEvent> events = auditService.getEventsByFlag("flag-1");
        assertEquals(1, events.size());
        assertEquals("FLAG_UPDATED", events.get(0).eventType());
        assertTrue((Boolean) events.get(0).details().get("nameChanged"));
        assertTrue((Boolean) events.get(0).details().get("enabledChanged"));
        assertTrue((Boolean) events.get(0).details().get("typeChanged"));
    }

    private static class NoopAuditPort implements AuditPort {
        @Override
        public void record(String actorType, String action, String category,
                           String resourceType, String resourceId, Map<String, Object> payload) {
        }
    }
}
