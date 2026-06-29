package com.example.platform.render.domain.timeline.render.effect;

/**
 * Status of an FFmpeg baseline effect planning result.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectPlanningResultStatus {
    PLANNED,
    VALIDATION_FAILED,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
