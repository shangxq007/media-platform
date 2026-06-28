package com.example.platform.render.domain.workflow;

import java.util.Map;

/**
 * Result of a workflow step (semantic/dry-run only in P2W.0).
 * Internal domain model.
 */
public record WorkflowStepResult(
        WorkflowStepId stepId,
        WorkflowStepType stepType,
        WorkflowStepStatus status,
        String safeMessage,
        Map<String, String> safeMetadata) {

    public static WorkflowStepResult pending(WorkflowStepId id, WorkflowStepType type) {
        return new WorkflowStepResult(id, type, WorkflowStepStatus.PENDING, "Pending", null);
    }

    public static WorkflowStepResult notImplemented(WorkflowStepId id, WorkflowStepType type, String reason) {
        return new WorkflowStepResult(id, type, WorkflowStepStatus.NOT_IMPLEMENTED, reason, null);
    }

    public static WorkflowStepResult succeeded(WorkflowStepId id, WorkflowStepType type, String message) {
        return new WorkflowStepResult(id, type, WorkflowStepStatus.SUCCEEDED, message, null);
    }
}
