package com.example.platform.shared.capability;

import java.time.Instant;
import java.util.List;

/**
 * Represents an execution record for an automation flow.
 *
 * <p><strong>Contract only:</strong> This defines the execution record shape.
 * No persistence or scheduler is implemented.</p>
 */
public record AutomationExecution(
    String executionId,
    String flowId,
    String tenantId,
    InvocationStatus status,
    Instant startedAt,
    Instant completedAt,
    String idempotencyKey,
    String errorCode,
    String logsRef,
    List<StepExecution> stepExecutions
) {
    public record StepExecution(
        String stepId,
        String actionKey,
        InvocationStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorCode
    ) {}
}
