package com.example.platform.render.domain.effect;

/**
 * Target types for FFmpeg baseline effect operations.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectOperationTargetType {
    TIMELINE,
    TRACK,
    CLIP,
    CAPTION,
    WATERMARK,
    OVERLAY
}
