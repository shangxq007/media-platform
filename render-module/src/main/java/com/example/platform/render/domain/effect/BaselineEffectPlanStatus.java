package com.example.platform.render.domain.effect;

/**
 * Status of a baseline effect plan.
 * Immutable enum. Internal domain model.
 */
public enum BaselineEffectPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
