package com.example.platform.render.domain.timeline.render.effect;

/**
 * Severity of an FFmpeg baseline effect plan issue.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectPlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
