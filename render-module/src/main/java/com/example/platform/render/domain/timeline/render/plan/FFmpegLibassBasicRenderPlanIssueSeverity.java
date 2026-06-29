package com.example.platform.render.domain.timeline.render.plan;

/**
 * Severity of a render plan issue.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderPlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
