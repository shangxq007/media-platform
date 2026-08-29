package com.example.platform.render.domain.effect;

/**
 * Status of a baseline effect planning result.
 * Immutable enum. Internal domain model.
 */
public enum BaselineEffectPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
