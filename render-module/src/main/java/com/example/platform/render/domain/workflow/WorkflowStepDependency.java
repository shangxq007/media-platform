package com.example.platform.render.domain.workflow;

/**
 * Dependency edge between workflow steps.
 * Internal domain model.
 */
public record WorkflowStepDependency(
        WorkflowStepId dependsOnStepId,
        String description) {

    public WorkflowStepDependency {
        if (dependsOnStepId == null)
            throw new IllegalArgumentException("Dependency step ID must not be null");
    }
}
