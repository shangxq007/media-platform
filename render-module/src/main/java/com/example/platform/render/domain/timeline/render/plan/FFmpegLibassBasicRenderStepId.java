package com.example.platform.render.domain.timeline.render.plan;

import java.util.Objects;

/**
 * Typed identifier for a render step.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStepId(String value) {
    public FFmpegLibassBasicRenderStepId {
        Objects.requireNonNull(value, "FFmpegLibassBasicRenderStepId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegLibassBasicRenderStepId.value must not be blank");
    }
}
