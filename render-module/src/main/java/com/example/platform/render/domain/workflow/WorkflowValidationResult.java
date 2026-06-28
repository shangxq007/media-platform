package com.example.platform.render.domain.workflow;

import java.util.List;

/**
 * Validation result for workflow application.
 * Internal domain model.
 */
public record WorkflowValidationResult(
        boolean valid,
        List<WorkflowValidationError> errors) {

    public static WorkflowValidationResult success() {
        return new WorkflowValidationResult(true, List.of());
    }

    public static WorkflowValidationResult failure(List<WorkflowValidationError> errors) {
        return new WorkflowValidationResult(false, errors);
    }
}
