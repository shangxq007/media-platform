package com.example.platform.render.domain.workflow;

/**
 * Stable identifier for a WorkflowStep.
 * Internal domain model.
 */
public record WorkflowStepId(String value) {
    public WorkflowStepId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("WorkflowStepId must not be blank");
    }
}
