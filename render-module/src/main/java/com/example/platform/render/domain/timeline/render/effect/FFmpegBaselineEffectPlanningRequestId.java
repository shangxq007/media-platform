package com.example.platform.render.domain.timeline.render.effect;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline effect planning request.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanningRequestId(String value) {
    public FFmpegBaselineEffectPlanningRequestId {
        Objects.requireNonNull(value, "FFmpegBaselineEffectPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineEffectPlanningRequestId.value must not be blank");
    }
}
