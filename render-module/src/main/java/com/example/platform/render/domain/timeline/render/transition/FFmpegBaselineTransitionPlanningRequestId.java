package com.example.platform.render.domain.timeline.render.transition;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg baseline transition planning request.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionPlanningRequestId(String value) {
    public FFmpegBaselineTransitionPlanningRequestId {
        Objects.requireNonNull(value, "FFmpegBaselineTransitionPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegBaselineTransitionPlanningRequestId.value must not be blank");
    }
}
