package com.example.platform.render.domain.plan;

/**
 * Status of a render stage.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderStageStatus {
    PLANNED,
    VALID,
    INVALID,
    BLOCKED,
    SKIPPED
}
