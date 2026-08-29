package com.example.platform.workflow.execution.app;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionId;
import com.example.platform.workflow.execution.domain.WorkflowExecutionStatus;
import com.example.platform.workflow.execution.domain.WorkflowExecutionTrigger;
import com.example.platform.workflow.execution.port.WorkflowExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UWEV1 execution application-service contract tests (C2): PUBLISHED-only start,
 * version pinning, idempotency, cancel.
 */
class WorkflowExecutionServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);

    private WorkflowExecutionRepository execRepo;
    private FakeDefRepo defRepo;
    private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        execRepo = new FakeExecRepo();
        defRepo = new FakeDefRepo();
        defRepo.def = publishedDef(1);  // default: PUBLISHED v1
        service = new WorkflowExecutionService(execRepo, defRepo, FIXED);
    }

    private UserWorkflowDefinition publishedDef(int version) {
        return new UserWorkflowDefinition(
                new UserWorkflowDefinitionId("def-1"),
                new UserWorkflowDefinitionVersion(version),
                "t-1", null, "Test", "desc",
                UserWorkflowDefinitionStatus.PUBLISHED,
                List.of(), List.of(), List.of(), null,
                1, 1, Instant.parse("2026-08-01T00:00:00Z"), "u-1",
                Instant.parse("2026-08-01T00:00:00Z"), "u-1",
                Instant.parse("2026-08-02T00:00:00Z"), "u-1", null, null);
    }

    private StartWorkflowExecutionCommand cmd(String idemKey) {
        return new StartWorkflowExecutionCommand(
                "t-1", new CanonicalActorRef("u-1", "USER"), "def-1", null,
                WorkflowExecutionTrigger.MANUAL, "[]", idemKey);
    }

    @Test
    void startPinsPublishedVersion() {
        WorkflowExecution e = service.start(cmd("idem-1"));
        assertEquals(WorkflowExecutionStatus.PENDING, e.status());
        assertEquals(1, e.definitionVersion());
        assertEquals("def-1", e.definitionId());
        assertTrue(!e.executionId().executionId().equals("def-1"));
    }

    @Test
    void draftDefinitionRejected() {
        defRepo.def = new UserWorkflowDefinition(
                new UserWorkflowDefinitionId("def-1"),
                new UserWorkflowDefinitionVersion(1),
                "t-1", null, "Draft", "desc",
                UserWorkflowDefinitionStatus.DRAFT,
                List.of(), List.of(), List.of(), null,
                1, 1, Instant.parse("2026-08-01T00:00:00Z"), "u-1",
                Instant.parse("2026-08-01T00:00:00Z"), "u-1", null, null, null, null);
        WorkflowExecutionException ex = assertThrows(WorkflowExecutionException.class,
                () -> service.start(cmd("idem-2")));
        assertEquals(WorkflowExecutionException.Code.DEFINITION_NOT_PUBLISHED, ex.code());
    }

    @Test
    void archivedDefinitionRejected() {
        defRepo.def = new UserWorkflowDefinition(
                new UserWorkflowDefinitionId("def-1"),
                new UserWorkflowDefinitionVersion(1),
                "t-1", null, "Archived", "desc",
                UserWorkflowDefinitionStatus.ARCHIVED,
                List.of(), List.of(), List.of(), null,
                1, 1, Instant.parse("2026-08-01T00:00:00Z"), "u-1",
                Instant.parse("2026-08-01T00:00:00Z"), "u-1",
                Instant.parse("2026-08-02T00:00:00Z"), "u-1",
                Instant.parse("2026-08-03T00:00:00Z"), "u-1");
        WorkflowExecutionException ex = assertThrows(WorkflowExecutionException.class,
                () -> service.start(cmd("idem-3")));
        assertEquals(WorkflowExecutionException.Code.DEFINITION_NOT_PUBLISHED, ex.code());
    }

    @Test
    void missingTenantRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StartWorkflowExecutionCommand(
                "", new CanonicalActorRef("u-1", "USER"), "def-1", null,
                WorkflowExecutionTrigger.MANUAL, "[]", "idem"));
    }

    @Test
    void idempotencyKeyReturnsExisting() {
        WorkflowExecution first = service.start(cmd("idem-same"));
        WorkflowExecution second = service.start(cmd("idem-same"));
        assertEquals(first.executionId(), second.executionId());
    }

    @Test
    void cancelTransitionsToCancelled() {
        WorkflowExecution e = service.start(cmd("idem-cancel"));
        WorkflowExecution cancelled = service.cancel(
                e.executionId(), new CanonicalActorRef("u-1", "USER"), "user requested");
        assertEquals(WorkflowExecutionStatus.CANCELLED, cancelled.status());
    }

    @Test
    void cancelTerminalIsIdempotent() {
        WorkflowExecution e = service.start(cmd("idem-cancel2"));
        service.cancel(e.executionId(), new CanonicalActorRef("u-1", "USER"), "first");
        WorkflowExecution again = service.cancel(
                e.executionId(), new CanonicalActorRef("u-1", "USER"), "second");
        assertEquals(WorkflowExecutionStatus.CANCELLED, again.status());
    }

    // ── fakes ─────────────────────────────────────────────────────────────
    static class FakeExecRepo implements WorkflowExecutionRepository {
        final java.util.Map<String, WorkflowExecution> store = new java.util.HashMap<>();

        @Override
        public void insert(WorkflowExecution execution) {
            store.put(execution.executionId().executionId() + "|" + execution.executionId().tenantId(), execution);
        }

        @Override
        public void updateStatus(WorkflowExecution execution) {
            store.put(execution.executionId().executionId() + "|" + execution.executionId().tenantId(), execution);
        }

        @Override
        public Optional<WorkflowExecution> findById(WorkflowExecutionId id) {
            return Optional.ofNullable(store.get(id.executionId() + "|" + id.tenantId()));
        }

        @Override
        public Optional<WorkflowExecution> findByIdempotencyKey(String tenantId, String idempotencyKey) {
            return store.values().stream()
                    .filter(e -> e.idempotencyKey().equals(idempotencyKey) && e.executionId().tenantId().equals(tenantId))
                    .findFirst();
        }
    }

    static class FakeDefRepo implements UserWorkflowDefinitionRepository {
        UserWorkflowDefinition def = null;

        @Override
        public void insertDraft(UserWorkflowDefinition draft) {
        }

        @Override
        public Optional<UserWorkflowDefinition> findExactVersion(
                String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version) {
            return def == null ? Optional.empty() : Optional.of(def);
        }

        @Override
        public Optional<UserWorkflowDefinition> findLatest(String tenantId, UserWorkflowDefinitionId definitionId) {
            return def == null ? Optional.empty() : Optional.of(def);
        }

        @Override
        public java.util.List<UserWorkflowDefinition> listByTenant(String tenantId, String projectId) {
            return def == null ? java.util.List.of() : java.util.List.of(def);
        }

        @Override
        public UserWorkflowDefinition updateDraft(UserWorkflowDefinition updated) {
            return updated;
        }

        @Override
        public void insertVersion(UserWorkflowDefinition newDraft) {
        }

        @Override
        public UserWorkflowDefinition archive(UserWorkflowDefinition archived) {
            return archived;
        }

        @Override
        public UserWorkflowDefinition publish(UserWorkflowDefinition published) {
            return published;
        }
    }
}
