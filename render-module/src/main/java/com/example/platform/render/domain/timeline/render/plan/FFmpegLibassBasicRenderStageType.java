package com.example.platform.render.domain.timeline.render.plan;

/**
 * Types of render stages in a basic render plan.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderStageType {
    VALIDATE_TIMELINE,
    PREPARE_INPUTS,
    PLAN_CLIP_SEQUENCE,
    PLAN_EFFECTS,
    PLAN_TRANSITIONS,
    PLAN_CAPTION_OVERLAYS,
    PLAN_WATERMARK_OVERLAYS,
    PLAN_AUDIO,
    PLAN_METADATA,
    PLAN_FINAL_ASSEMBLY,
    PLAN_OUTPUT_ENCODING,
    PLAN_OUTPUT_VERIFICATION
}
