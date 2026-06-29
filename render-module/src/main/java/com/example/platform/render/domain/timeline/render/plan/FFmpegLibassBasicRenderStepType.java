package com.example.platform.render.domain.timeline.render.plan;

/**
 * Types of render steps in a basic render plan.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegLibassBasicRenderStepType {
    VALIDATE_TIMELINE,
    DECLARE_INPUT_CLIP,
    DECLARE_OUTPUT_PROFILE,
    APPLY_EFFECT_OPERATION,
    APPLY_TRANSITION_OPERATION,
    APPLY_CAPTION_OVERLAY,
    APPLY_WATERMARK_OVERLAY,
    ASSEMBLE_CLIP_SEQUENCE,
    ENCODE_OUTPUT,
    VERIFY_OUTPUT,
    DECLARE_AUDIO_TRACK,
    APPLY_AUDIO_OPERATION,
    DECLARE_SAFE_METADATA
}
