package com.example.platform.render.domain.timeline.render.plan;

import java.util.Objects;

/**
 * Typed identifier for an FFmpeg/libass basic render plan.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderPlanId(String value) {
    public FFmpegLibassBasicRenderPlanId {
        Objects.requireNonNull(value, "FFmpegLibassBasicRenderPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegLibassBasicRenderPlanId.value must not be blank");
    }
}
