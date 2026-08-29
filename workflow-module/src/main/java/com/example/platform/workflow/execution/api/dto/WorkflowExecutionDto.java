package com.example.platform.workflow.execution.api.dto;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionStatus;
import com.example.platform.workflow.execution.domain.WorkflowExecutionTrigger;

import java.time.Instant;

/**
 * Stable query DTO (workflow::execution public surface). Never exposes
 * Temporal internals / repository implementations.
 */
public record WorkflowExecutionDto(
        String executionId,
        String tenantId,
        CanonicalActorRef actor,
        String definitionId,
        int definitionVersion,
        WorkflowExecutionTrigger trigger,
        WorkflowExecutionStatus status,
        String temporalWorkflowId,
        String idempotencyKey,
        String resultSummaryJson,
        String errorCategory,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public static WorkflowExecutionDto from(WorkflowExecution e) {
        return new WorkflowExecutionDto(
                e.executionId().executionId(),
                e.executionId().tenantId(),
                e.actor(),
                e.definitionId(),
                e.definitionVersion(),
                e.trigger(),
                e.status(),
                e.temporalWorkflowId(),
                e.idempotencyKey(),
                e.resultSummaryJson(),
                e.errorCategory(),
                e.createdAt(),
                e.startedAt(),
                e.completedAt());
    }
}
