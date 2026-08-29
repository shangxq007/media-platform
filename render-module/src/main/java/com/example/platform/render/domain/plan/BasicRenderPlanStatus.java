package com.example.platform.render.domain.plan;

/**
 * Status of a basic render plan.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
