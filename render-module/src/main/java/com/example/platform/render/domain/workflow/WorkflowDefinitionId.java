package com.example.platform.render.domain.workflow;

/**
 * Stable identifier for a WorkflowDefinition.
 * Internal domain model.
 */
public record WorkflowDefinitionId(String value) {
    public WorkflowDefinitionId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("WorkflowDefinitionId must not be blank");
    }
}
