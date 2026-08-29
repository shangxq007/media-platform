package com.example.platform.render.domain.transition;

/**
 * Status of a baseline transition planning result.
 * Immutable enum. Internal domain model.
 */
public enum BaselineTransitionPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
