package com.example.platform.render.domain.transition;

/**
 * Status of a baseline transition plan.
 * Immutable enum. Internal domain model.
 */
public enum BaselineTransitionPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
