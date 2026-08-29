package com.example.platform.render.domain.effect;

import java.util.Map;
import java.util.Objects;

/**
 * Typed parameter for a baseline effect operation.
 * Immutable. Internal domain model.
 */
public record BaselineEffectOperationParameter(
        String name,
        BaselineEffectParameterType type,
        Object value,
        Map<String, String> safeMetadata
) {
    public BaselineEffectOperationParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
