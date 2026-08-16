package com.example.platform.render.domain.transition;

/**
 * Severity of an FFmpeg baseline transition plan issue.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionPlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
