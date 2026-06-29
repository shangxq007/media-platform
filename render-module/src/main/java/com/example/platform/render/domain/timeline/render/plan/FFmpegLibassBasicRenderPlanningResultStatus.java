package com.example.platform.render.domain.timeline.render.plan;

/**
 * Status of a render planning result.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
