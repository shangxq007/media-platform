package com.example.platform.render.domain.timeline.render.effect;

/**
 * FFmpeg baseline effect operation types.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectOperationType {
    // Production / baseline
    SCALE,
    CROP,
    FIT,
    FILL,
    CONTAIN,
    ROTATE,
    OPACITY,
    FADE_IN,
    FADE_OUT,
    TEXT_OVERLAY,
    IMAGE_OVERLAY,
    CAPTION_OVERLAY,
    WATERMARK_OVERLAY,
    // POC / future
    BLUR,
    COLOR_ADJUST,
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
    VOLUME_ADJUST,
    AUDIO_FADE_IN,
    AUDIO_FADE_OUT,
    PICTURE_IN_PICTURE,
    BACKGROUND_BLUR
}
