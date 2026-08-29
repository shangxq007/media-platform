package com.example.platform.render.domain.plan;

/**
 * Target types for render steps.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderStepTargetType {
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
