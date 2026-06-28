package com.example.platform.render.domain.workflow;

/**
 * Semantic version for a workflow definition.
 * Internal domain model.
 */
public record WorkflowVersion(String value) {
    public WorkflowVersion {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("WorkflowVersion must not be blank");
    }
}
