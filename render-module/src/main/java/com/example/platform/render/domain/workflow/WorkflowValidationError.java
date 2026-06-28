package com.example.platform.render.domain.workflow;

/**
 * Validation error for workflow application.
 * Internal domain model. No storage/provider details.
 */
public record WorkflowValidationError(
        String field,
        String code,
        String message) {}
