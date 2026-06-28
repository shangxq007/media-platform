package com.example.platform.render.domain.workflow;

import java.util.List;

/**
 * Result of workflow application — semantic/dry-run only in P2W.0.
 * Internal domain model. Does not execute workflow steps.
 */
public record WorkflowApplicationResult(
        WorkflowApplicationStatus status,
        WorkflowValidationResult validationResult,
        List<WorkflowStepResult> stepResults,
        String safeMessage) {

    public static WorkflowApplicationResult accepted(String message) {
        return new WorkflowApplicationResult(WorkflowApplicationStatus.ACCEPTED,
                WorkflowValidationResult.success(), List.of(), message);
    }

    public static WorkflowApplicationResult validationFailed(WorkflowValidationResult validation) {
        return new WorkflowApplicationResult(WorkflowApplicationStatus.VALIDATION_FAILED,
                validation, List.of(), "Workflow validation failed");
    }

    public static WorkflowApplicationResult notImplemented(String message) {
        return new WorkflowApplicationResult(WorkflowApplicationStatus.NOT_IMPLEMENTED,
                null, List.of(), message);
    }

    public static WorkflowApplicationResult failed(String message) {
        return new WorkflowApplicationResult(WorkflowApplicationStatus.FAILED,
                null, List.of(), message);
    }

    public boolean isSuccess() {
        return status == WorkflowApplicationStatus.ACCEPTED;
    }
}
