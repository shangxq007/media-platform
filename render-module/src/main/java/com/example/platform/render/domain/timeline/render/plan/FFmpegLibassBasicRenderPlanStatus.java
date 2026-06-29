package com.example.platform.render.domain.timeline.render.plan;

/**
 * Status of an FFmpeg/libass basic render plan.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderPlanStatus {
    READY,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED,
    UNSUPPORTED,
    FAILED
}
