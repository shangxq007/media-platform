package com.example.platform.render.domain.timeline.render.effect;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline effect operation.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectOperationId(String value) {
    public FFmpegBaselineEffectOperationId {
        Objects.requireNonNull(value, "FFmpegBaselineEffectOperationId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineEffectOperationId.value must not be blank");
    }
}
