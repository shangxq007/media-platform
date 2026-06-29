package com.example.platform.render.domain.timeline.render.plan;

import java.util.Objects;

/**
 * Typed identifier for a render planning request.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanningRequestId(String value) {
    public FFmpegLibassBasicRenderPlanningRequestId {
        Objects.requireNonNull(value, "FFmpegLibassBasicRenderPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegLibassBasicRenderPlanningRequestId.value must not be blank");
    }
}
