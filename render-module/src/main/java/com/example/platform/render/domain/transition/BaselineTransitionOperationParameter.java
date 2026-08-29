package com.example.platform.render.domain.transition;

import java.util.Map;
import java.util.Objects;

/**
 * Typed parameter for a baseline transition operation.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionOperationParameter(
        String name,
        BaselineTransitionParameterType type,
        Object value,
        Map<String, String> safeMetadata
) {
    public BaselineTransitionOperationParameter {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
