package com.example.platform.render.domain.timeline.render.effect;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline effect plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectPlanId(String value) {
    public FFmpegBaselineEffectPlanId {
        Objects.requireNonNull(value, "FFmpegBaselineEffectPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineEffectPlanId.value must not be blank");
    }
}
