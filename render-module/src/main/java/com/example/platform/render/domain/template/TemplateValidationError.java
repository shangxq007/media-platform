package com.example.platform.render.domain.template;

/**
 * Validation error for template application.
 * Internal domain model. No storage/provider details.
 */
public record TemplateValidationError(
        String field,
        String code,
        String message) {}
