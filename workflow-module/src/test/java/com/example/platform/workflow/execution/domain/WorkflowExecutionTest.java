package com.example.platform.workflow.execution.domain;

import com.example.platform.shared.usage.CanonicalActorRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UWEV1 execution domain contract tests (C1).
 */
class WorkflowExecutionTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    private WorkflowExecution pending(String tenant, String id) {
        var execId = new WorkflowExecutionId(id, tenant);
        return new WorkflowExecution(
                execId,
                new CanonicalActorRef("u-1", "USER"),
                "def-1", 1,
                WorkflowExecutionTrigger.MANUAL,
                WorkflowExecutionStatus.PENDING,
                execId.temporalWorkflowId(),
                "idem-" + id,
                null, null, null,
                NOW, null, null);
    }

    @Test
    void executionIdDistinctFromDefinitionId() {
        var execId = new WorkflowExecutionId("e-1", "t-1");
        assertTrue(!execId.executionId().equals("def-1"));
        assertEquals("uwe-t-1-e-1", execId.temporalWorkflowId());
        assertTrue(!execId.temporalWorkflowId().equals("def-1"));
    }

    @Test
    void tenantRequired() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowExecutionId("e-1", ""));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowExecutionId("", "t-1"));
    }

    @Test
    void lifecycleTransitions() {
        WorkflowExecution e = pending("t-1", "e-1");
        assertEquals(WorkflowExecutionStatus.PENDING, e.status());

        WorkflowExecution running = e.started(NOW.plusSeconds(1));
        assertEquals(WorkflowExecutionStatus.RUNNING, running.status());
        assertEquals(NOW.plusSeconds(1), running.startedAt());

        WorkflowExecution done = running.succeeded("{\"output\":\"ok\"}", NOW.plusSeconds(10));
        assertEquals(WorkflowExecutionStatus.SUCCEEDED, done.status());
        assertEquals("{\"output\":\"ok\"}", done.resultSummaryJson());

        WorkflowExecution failed = running.failed("boom", "EXECUTION_FAILED", NOW.plusSeconds(5));
        assertEquals(WorkflowExecutionStatus.FAILED, failed.status());
        assertEquals("EXECUTION_FAILED", failed.errorCategory());

        WorkflowExecution cancelled = running.cancelled("user cancel", NOW.plusSeconds(2));
        assertEquals(WorkflowExecutionStatus.CANCELLED, cancelled.status());
    }

    @Test
    void definitionVersionMustBePositive() {
        var execId = new WorkflowExecutionId("e-1", "t-1");
        assertThrows(IllegalArgumentException.class, () -> new WorkflowExecution(
                execId, new CanonicalActorRef("u-1", "USER"), "def-1", 0,
                WorkflowExecutionTrigger.MANUAL, WorkflowExecutionStatus.PENDING,
                execId.temporalWorkflowId(), "idem", null, null, null, NOW, null, null));
    }

    @Test
    void statusVocabularyIsExact() {
        // frozen 7-state vocabulary — no near-synonyms
        assertEquals(7, WorkflowExecutionStatus.values().length);
    }
}
