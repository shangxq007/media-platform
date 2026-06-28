package com.example.platform.render.domain.workflow;

/**
 * Workflow parameter with name and value.
 * Internal domain model.
 */
public record WorkflowParameter(
        String name,
        WorkflowParameterValue value) {}
