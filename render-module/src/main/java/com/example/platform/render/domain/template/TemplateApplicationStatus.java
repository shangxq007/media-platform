package com.example.platform.render.domain.template;

/**
 * Status of a template application result.
 * Internal domain model.
 */
public enum TemplateApplicationStatus {
    SUCCESS,
    VALIDATION_FAILED,
    COMPILATION_FAILED,
    UNSUPPORTED_OPERATION,
    MISSING_TARGET,
    FAILED_CLOSED
}
