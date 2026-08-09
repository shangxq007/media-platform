package com.example.platform.workflow.execution.domain;

import com.example.platform.billing.usage.CanonicalActorRef;

import java.time.Instant;
import java.util.Objects;

/**
 * Canonical User Workflow Execution aggregate (UWE-ADR-002/004/006).
 *
 * <p>Product/query/audit authority projection of a durable execution. The
 * Temporal execution history is the orchestration runtime authority; this
 * entity is the platform product authority (minimal single-table persistence).</p>
 *
 * <p>Definition version is PINNED immutable at start (UWE-ADR-003): the
 * execution never follows a later mutable definition version.</p>
 */
public record WorkflowExecution(
        WorkflowExecutionId executionId,
        CanonicalActorRef actor,
        String definitionId,
        int definitionVersion,
        WorkflowExecutionTrigger trigger,
        WorkflowExecutionStatus status,
        String temporalWorkflowId,
        String idempotencyKey,
        String inputRefsJson,
        String resultSummaryJson,
        String errorCategory,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public WorkflowExecution {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(actor, "actor");
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("definitionId must not be blank");
        }
        if (definitionVersion <= 0) {
            throw new IllegalArgumentException("definitionVersion must be positive");
        }
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(status, "status");
        if (temporalWorkflowId == null || temporalWorkflowId.isBlank()) {
            throw new IllegalArgumentException("temporalWorkflowId must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Transitions into RUNNING (started). */
    public WorkflowExecution started(Instant now) {
        return new WorkflowExecution(
                executionId, actor, definitionId, definitionVersion, trigger,
                WorkflowExecutionStatus.RUNNING, temporalWorkflowId, idempotencyKey,
                inputRefsJson, null, null, createdAt, now, null);
    }

    /** Terminal success. */
    public WorkflowExecution succeeded(String resultSummary, Instant now) {
        return new WorkflowExecution(
                executionId, actor, definitionId, definitionVersion, trigger,
                WorkflowExecutionStatus.SUCCEEDED, temporalWorkflowId, idempotencyKey,
                inputRefsJson, resultSummary, null, createdAt, startedAt, now);
    }

    /** Terminal failure with canonical error category. */
    public WorkflowExecution failed(String resultSummary, String errorCategory, Instant now) {
        return new WorkflowExecution(
                executionId, actor, definitionId, definitionVersion, trigger,
                WorkflowExecutionStatus.FAILED, temporalWorkflowId, idempotencyKey,
                inputRefsJson, resultSummary, errorCategory, createdAt, startedAt, now);
    }

    /** Terminal cancellation. */
    public WorkflowExecution cancelled(String resultSummary, Instant now) {
        return new WorkflowExecution(
                executionId, actor, definitionId, definitionVersion, trigger,
                WorkflowExecutionStatus.CANCELLED, temporalWorkflowId, idempotencyKey,
                inputRefsJson, resultSummary, null, createdAt, startedAt, now);
    }
}
