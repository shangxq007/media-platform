package com.example.platform.render.domain.template.composite;

/**
 * Validation error for composite template.
 * Internal domain model. No provider/storage details.
 */
public record CompositeTemplateValidationError(
        String field,
        String code,
        String message) {}
