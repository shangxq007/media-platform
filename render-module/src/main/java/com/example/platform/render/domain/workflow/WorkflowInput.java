package com.example.platform.render.domain.workflow;

/**
 * Workflow input declaration.
 * Internal domain model. No local paths or storage internals.
 */
public record WorkflowInput(
        String name,
        String type,
        boolean required,
        String description) {}
