package com.example.platform.render.domain.timeline.render.transition;

/**
 * Status of an FFmpeg baseline transition plan.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
