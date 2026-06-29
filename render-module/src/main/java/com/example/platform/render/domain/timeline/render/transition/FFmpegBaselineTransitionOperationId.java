package com.example.platform.render.domain.timeline.render.transition;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline transition operation.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionOperationId(String value) {
    public FFmpegBaselineTransitionOperationId {
        Objects.requireNonNull(value, "FFmpegBaselineTransitionOperationId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineTransitionOperationId.value must not be blank");
    }
}
