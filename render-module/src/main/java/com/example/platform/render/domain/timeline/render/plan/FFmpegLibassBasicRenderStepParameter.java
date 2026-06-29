package com.example.platform.render.domain.timeline.render.plan;

import java.util.Map;
import java.util.Objects;

/**
 * Typed parameter for a render step.
 * Immutable. Internal domain model.
 */
public record FFmpegLibassBasicRenderStepParameter(
        String name,
        FFmpegLibassBasicRenderStepParameterType type,
        Object value,
        Map<String, String> safeMetadata
) {
    public FFmpegLibassBasicRenderStepParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
