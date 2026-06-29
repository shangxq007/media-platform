package com.example.platform.render.domain.timeline.render.plan;

/**
 * Target types for render steps.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderStepTargetType {
    TIMELINE,
    TRACK,
    CLIP,
    CAPTION,
    WATERMARK,
    EFFECT_OPERATION,
    TRANSITION_OPERATION,
    OUTPUT_PROFILE,
    FINAL_OUTPUT
}
