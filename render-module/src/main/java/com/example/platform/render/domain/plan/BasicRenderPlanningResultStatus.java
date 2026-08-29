package com.example.platform.render.domain.plan;

/**
 * Status of a render planning result.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
