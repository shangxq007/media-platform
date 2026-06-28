package com.example.platform.render.domain.workflow;

/**
 * Workflow output declaration.
 * Internal domain model. No local paths or storage internals.
 */
public record WorkflowOutput(
        String name,
        String type,
        String description) {}
