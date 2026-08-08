package com.example.platform.workflow.definition.domain;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UWD-RED-001..004 (DOMAIN). Authentic RED: fails at compile time until the
 * W2 domain aggregate exists.
 */
class UserWorkflowDefinitionTest {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");

    private static UserWorkflowDefinition draft(String tenantId, String name) {
        return new UserWorkflowDefinition(
                UserWorkflowDefinitionId.generate(),
                UserWorkflowDefinitionVersion.of(1),
                tenantId, null, name, null,
                UserWorkflowDefinitionStatus.DRAFT,
                List.of(), List.of(), List.of(),
                UserWorkflowTriggerBinding.manual(),
                1, 1L, NOW, "u-1", NOW, "u-1", null, null, null, null);
    }

    @Test
    void createRejectsBlankTenantId() {
        assertThrows(IllegalArgumentException.class, () -> draft("  ", "wf"));
    }

    @Test
    void illegalLifecycleTransitionRejected() {
        UserWorkflowDefinition d = draft("tenant-a", "wf");
        UserWorkflowException ex = assertThrows(UserWorkflowException.class, () -> d.publish("u-1", NOW));
        assertEquals(UserWorkflowErrorCode.Code.INVALID_LIFECYCLE_TRANSITION, ex.errorCode());
        UserWorkflowDefinition validated = d.markValidated("u-1", NOW);
        UserWorkflowDefinition published = validated.publish("u-1", NOW);
        assertEquals(UserWorkflowDefinitionStatus.PUBLISHED, published.status());
        // published -> draft is prohibited
        assertThrows(UserWorkflowException.class, () -> published.reopenDraft("u-1", NOW));
        // archived is terminal
        UserWorkflowDefinition archived = published.archive("u-1", NOW);
        assertEquals(UserWorkflowDefinitionStatus.ARCHIVED, archived.status());
        assertThrows(UserWorkflowException.class, () -> archived.archive("u-1", NOW));
    }

    @Test
    void publishedVersionIsImmutable() {
        UserWorkflowDefinition p = draft("tenant-a", "wf").markValidated("u-1", NOW).publish("u-1", NOW);
        UserWorkflowException ex = assertThrows(UserWorkflowException.class,
                () -> p.updatedDraft("new-name", "desc", List.of(), List.of(), List.of(),
                        p.triggerBinding(), "u-1", NOW));
        assertEquals(UserWorkflowErrorCode.Code.PUBLISHED_IMMUTABLE, ex.errorCode());
    }

    @Test
    void createVersionCopiesPublishedContentAndResetsLifecycle() {
        UserWorkflowDefinition source = draft("tenant-a", "wf");
        UserWorkflowDefinition published = source.markValidated("u-1", NOW).publish("u-1", NOW);
        UserWorkflowDefinition next = published.createNextVersion("u-1", NOW);
        assertEquals(UserWorkflowDefinitionStatus.DRAFT, next.status());
        assertEquals(2, next.version().versionNumber());
        assertEquals(published.name(), next.name());
        assertEquals(published.nodes(), next.nodes());
        assertEquals(published.edges(), next.edges());
        assertEquals(1, next.optimisticVersion());
        assertNull(next.publishedAt());
        assertNull(next.archivedAt());
        // DRAFT source cannot create a version
        assertThrows(UserWorkflowException.class, () -> source.createNextVersion("u-1", NOW));
    }

    @Test
    void validateThenReopenRoundTrips() {
        UserWorkflowDefinition d = draft("tenant-a", "wf");
        UserWorkflowDefinition v = d.markValidated("u-1", NOW);
        assertEquals(UserWorkflowDefinitionStatus.VALIDATED, v.status());
        UserWorkflowDefinition reopened = v.reopenDraft("u-1", NOW);
        assertEquals(UserWorkflowDefinitionStatus.DRAFT, reopened.status());
    }
}
