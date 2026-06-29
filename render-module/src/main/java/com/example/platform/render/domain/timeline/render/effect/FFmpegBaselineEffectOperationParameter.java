package com.example.platform.render.domain.timeline.render.effect;

import java.util.Map;
import java.util.Objects;

/**
 * Typed parameter for an FFmpeg baseline effect operation.
 * Immutable. Internal domain model.
 */
public record FFmpegBaselineEffectOperationParameter(
        String name,
        FFmpegBaselineEffectParameterType type,
        Object value,
        Map<String, String> safeMetadata
) {
    public FFmpegBaselineEffectOperationParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
