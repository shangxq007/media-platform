package com.example.platform.render.domain.timeline.render.effect;

/**
 * Parameter types for FFmpeg baseline effect operations.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineEffectParameterType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DURATION_MS,
    PERCENT,
    PIXEL,
    RATIO,
    ENUM,
    COLOR,
    SAFE_REF
}
