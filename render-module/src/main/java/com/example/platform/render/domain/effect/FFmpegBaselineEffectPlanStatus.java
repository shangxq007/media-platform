package com.example.platform.render.domain.effect;

/**
 * Status of an FFmpeg baseline effect plan.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
