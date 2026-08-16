package com.example.platform.render.domain.plan;

/**
 * Status of a render stage.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderStageStatus {
    PLANNED,
    VALID,
    INVALID,
    BLOCKED,
    SKIPPED
}
