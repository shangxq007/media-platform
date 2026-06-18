package com.example.platform.shared.capability.trace;

import com.example.platform.shared.capability.AutomationTrigger;
import com.example.platform.shared.capability.validation.AutomationFlowValidationIssue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the execution trace of an automation flow.
 *
 * <p><strong>Contract only:</strong> This defines the execution trace shape.
 * Persistence is not implemented.</p>
 */
public record AutomationExecutionTrace(
    String executionId,
    String flowId,
    String flowVersion,
    String tenantId,
    AutomationTrigger.TriggerType triggerType,
    String triggerRef,
    AutomationExecutionTraceStatus status,
    Instant startedAt,
    Instant completedAt,
    boolean dryRun,
    String correlationId,
    String causationId,
    String idempotencyKey,
    List<AutomationNodeExecutionTrace> nodeTraces,
    List<AutomationFlowValidationIssue> issues,
    String logsRef,
    Map<String, Object> metrics
) {
    public AutomationExecutionTrace {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (flowId == null || flowId.isBlank()) {
            throw new IllegalArgumentException("flowId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        nodeTraces = nodeTraces != null ? List.copyOf(nodeTraces) : List.of();
        issues = issues != null ? List.copyOf(issues) : List.of();
        metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
    }

    /**
     * Check if all nodes succeeded.
     */
    public boolean allNodesSucceeded() {
        return nodeTraces.stream()
            .allMatch(t -> t.status() == AutomationNodeExecutionTraceStatus.SUCCEEDED
                || t.status() == AutomationNodeExecutionTraceStatus.DRY_RUN_SUCCEEDED);
    }

    /**
     * Check if any nodes are not implemented.
     */
    public boolean hasNotImplementedNodes() {
        return nodeTraces.stream()
            .anyMatch(t -> t.status() == AutomationNodeExecutionTraceStatus.NOT_IMPLEMENTED);
    }

    /**
     * Get the duration of the execution.
     */
    public java.time.Duration duration() {
        if (startedAt == null || completedAt == null) {
            return java.time.Duration.ZERO;
        }
        return java.time.Duration.between(startedAt, completedAt);
    }
}
