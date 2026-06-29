package com.example.platform.render.domain.timeline.render.transition;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline transition plan.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanId(String value) {
    public FFmpegBaselineTransitionPlanId {
        Objects.requireNonNull(value, "FFmpegBaselineTransitionPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineTransitionPlanId.value must not be blank");
    }
}
