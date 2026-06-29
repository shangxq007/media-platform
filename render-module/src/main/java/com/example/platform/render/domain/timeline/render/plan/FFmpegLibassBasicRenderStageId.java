package com.example.platform.render.domain.timeline.render.plan;

import java.util.Objects;

/**
 * Typed identifier for a render stage.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStageId(String value) {
    public FFmpegLibassBasicRenderStageId {
        Objects.requireNonNull(value, "FFmpegLibassBasicRenderStageId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FFmpegLibassBasicRenderStageId.value must not be blank");
    }
}
