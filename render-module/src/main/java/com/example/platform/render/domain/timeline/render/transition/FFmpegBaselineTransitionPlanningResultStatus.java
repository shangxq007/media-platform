package com.example.platform.render.domain.timeline.render.transition;

/**
 * Status of an FFmpeg baseline transition planning result.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
