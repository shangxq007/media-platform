package com.example.platform.render.domain.plan;

/**
 * Source of a render step.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderStepSource {
    TIMELINE_VALIDATION,
    CLIP_SEQUENCE,
    EFFECT_PLAN,
    TRANSITION_PLAN,
    CAPTION_OVERLAY,
    WATERMARK_OVERLAY,
    OUTPUT_PROFILE,
    FINAL_ASSEMBLY,
    VERIFICATION
}
