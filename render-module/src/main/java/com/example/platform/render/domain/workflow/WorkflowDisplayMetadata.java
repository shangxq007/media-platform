package com.example.platform.render.domain.workflow;

/**
 * Display metadata for a workflow definition.
 * Internal domain model. No provider/storage internals.
 */
public record WorkflowDisplayMetadata(
        String name,
        String description,
        String category) {}
