package com.example.platform.render.domain.timeline.render.effect;

/**
 * Source of an FFmpeg baseline effect operation.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectOperationSource {
    BASIC_TIMELINE_EFFECT_REF,
    VISUAL_CAPABILITY_RESOLVED,
    POLICY_DEFAULT,
    INTERNAL_ANNOTATION
}
