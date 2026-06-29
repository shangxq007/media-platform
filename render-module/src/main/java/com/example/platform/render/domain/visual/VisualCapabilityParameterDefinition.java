package com.example.platform.render.domain.visual;

import java.util.Map;
import java.util.Objects;

/**
 * Definition of a single parameter for a visual capability.
 * Immutable record. Internal domain model.
 */
public record VisualCapabilityParameterDefinition(
        String name,
        VisualCapabilityParameterType type,
        boolean required,
        Object defaultValue,
        String description,
        Map<String, String> safeMetadata
) {
    public VisualCapabilityParameterDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
