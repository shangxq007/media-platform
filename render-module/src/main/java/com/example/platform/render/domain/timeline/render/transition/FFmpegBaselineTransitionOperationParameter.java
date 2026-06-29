package com.example.platform.render.domain.timeline.render.transition;

import java.util.Map;
import java.util.Objects;

/**
 * Typed parameter for an FFmpeg baseline transition operation.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineTransitionOperationParameter(
        String name,
        FFmpegBaselineTransitionParameterType type,
        Object value,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineTransitionOperationParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
