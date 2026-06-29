package com.example.platform.render.domain.timeline.render.transition;

/**
 * Parameter types for FFmpeg baseline transition operations.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionParameterType {
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
