package com.example.platform.workflow.definition.app;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.domain.WorkflowNodeType;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UWD-RED-012..013 (DOMAIN). Authentic RED: fails at compile time until the
 * W2 application service and repository port exist.
 */
class UserWorkflowDefinitionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryWorkflowRepository repo = new InMemoryWorkflowRepository();
    private final UserWorkflowDefinitionService service =
            new UserWorkflowDefinitionService(repo, CLOCK);

    private static UserWorkflowDefinitionNode actionNode(String id) {
        return new UserWorkflowDefinitionNode(id, WorkflowNodeType.ACTION, "node-" + id,
                "w2/action/config/v1",
                new UserWorkflowDefinitionNode.VersionedJsonDocument(1,
                        "{\"capabilityKey\":\"render.render-job.create\",\"capabilityVersion\":\"1\"}"),
                List.of(), List.of(), UserWorkflowDefinitionNode.ErrorPolicy.FAIL);
    }

    @Test
    void createValidatePublishRoundtrip() {
        UserWorkflowDefinition created = service.create("tenant-a", null, "wf", null,
                List.of(actionNode("n0")), List.of(),
                List.of(), UserWorkflowTriggerBinding.manual(), 1, "u-1");
        assertEquals(UserWorkflowDefinitionStatus.DRAFT, created.status());
        assertEquals(1, created.version().versionNumber());

        var result = service.validate("tenant-a", created.definitionId(), created.version(), "u-1");
        assertEquals(true, result.valid());

        UserWorkflowDefinition published = service.publish("tenant-a", created.definitionId(),
                created.version(), 2L, "u-1");
        assertEquals(UserWorkflowDefinitionStatus.PUBLISHED, published.status());
    }

    @Test
    void crossTenantAccessDenied() {
        UserWorkflowDefinition created = service.create("tenant-a", null, "wf", null,
                List.of(actionNode("n0")), List.of(),
                List.of(), UserWorkflowTriggerBinding.manual(), 1, "u-1");
        UserWorkflowException ex = assertThrows(UserWorkflowException.class,
                () -> service.get("tenant-b", created.definitionId()));
        assertEquals(UserWorkflowDefinition.UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND, ex.errorCode());
    }

    @Test
    void staleOptimisticVersionRejected() {
        UserWorkflowDefinition created = service.create("tenant-a", null, "wf", null,
                List.of(actionNode("n0")), List.of(),
                List.of(), UserWorkflowTriggerBinding.manual(), 1, "u-1");
        UserWorkflowException ex = assertThrows(UserWorkflowException.class,
                () -> service.updateDraft("tenant-a", created.definitionId(), created.version(),
                        99L, "wf2", null, List.of(actionNode("n0")), List.of(),
                        List.of(), UserWorkflowTriggerBinding.manual(), "u-1"));
        assertEquals(UserWorkflowDefinition.UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT, ex.errorCode());
    }

    @Test
    void invalidGraphRejectedOnCreate() {
        // self edge -> G-006 -> blocking -> VALIDATION_FAILED (422 semantics)
        var edge = com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge
                .unconditional("e1", "n0", "n0", 0);
        UserWorkflowException ex = assertThrows(UserWorkflowException.class,
                () -> service.create("tenant-a", null, "wf", null,
                        List.of(actionNode("n0")), List.of(edge),
                        List.of(), UserWorkflowTriggerBinding.manual(), 1, "u-1"));
        assertEquals(UserWorkflowDefinition.UserWorkflowErrorCode.Code.VALIDATION_FAILED, ex.errorCode());
    }

    /** In-memory port implementation mirroring the JDBC adapter CAS semantics. */
    static final class InMemoryWorkflowRepository implements UserWorkflowDefinitionRepository {

        private final java.util.Map<String, UserWorkflowDefinition> store = new java.util.LinkedHashMap<>();

        private static String key(String tenantId, UserWorkflowDefinitionId id, UserWorkflowDefinitionVersion v) {
            return tenantId + "|" + id.value() + "|" + v.versionNumber();
        }

        @Override
        public void insertDraft(UserWorkflowDefinition draft) {
            String k = key(draft.tenantId(), draft.definitionId(), draft.version());
            if (store.containsKey(k)) {
                throw new UserWorkflowException(UserWorkflowDefinition.UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT, "duplicate definition version");
            }
            store.put(k, draft);
        }

        @Override
        public Optional<UserWorkflowDefinition> findExactVersion(String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version) {
            return Optional.ofNullable(store.get(key(tenantId, definitionId, version)));
        }

        @Override
        public Optional<UserWorkflowDefinition> findLatest(String tenantId, UserWorkflowDefinitionId definitionId) {
            return store.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId) && d.definitionId().equals(definitionId))
                    .max(java.util.Comparator.comparing(d -> d.version().versionNumber()));
        }

        @Override
        public List<UserWorkflowDefinition> listByTenant(String tenantId, String projectId) {
            return store.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId))
                    .filter(d -> projectId == null || projectId.equals(d.projectId()))
                    .sorted(java.util.Comparator.comparing(d -> d.definitionId().value()))
                    .toList();
        }

        @Override
        public UserWorkflowDefinition updateDraft(UserWorkflowDefinition updated) {
            String k = key(updated.tenantId(), updated.definitionId(), updated.version());
            UserWorkflowDefinition current = store.get(k);
            if (current == null) {
                throw new UserWorkflowException(UserWorkflowDefinition.UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND, "not found");
            }
            if (current.optimisticVersion() != updated.optimisticVersion()) {
                throw new UserWorkflowException(UserWorkflowDefinition.UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT, "stale optimistic version");
            }
            UserWorkflowDefinition next = withBumpedVersion(updated);
            store.put(k, next);
            return next;
        }

        @Override
        public UserWorkflowDefinition publish(UserWorkflowDefinition published) {
            return updateDraft(published);
        }

        @Override
        public UserWorkflowDefinition archive(UserWorkflowDefinition archived) {
            return updateDraft(archived);
        }

        @Override
        public void insertVersion(UserWorkflowDefinition newDraft) {
            insertDraft(newDraft);
        }

        private static UserWorkflowDefinition withBumpedVersion(UserWorkflowDefinition d) {
            return new UserWorkflowDefinition(d.definitionId(), d.version(), d.tenantId(), d.projectId(),
                    d.name(), d.description(), d.status(), d.nodes(), d.edges(), d.parameters(),
                    d.triggerBinding(), d.schemaVersion(), d.optimisticVersion() + 1,
                    d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
                    d.publishedAt(), d.publishedBy(), d.archivedAt(), d.archivedBy());
        }
    }
}
