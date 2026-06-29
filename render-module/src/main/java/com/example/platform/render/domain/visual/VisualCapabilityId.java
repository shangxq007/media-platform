package com.example.platform.render.domain.visual;

import java.util.Objects;

/**
 * Typed identifier for a visual capability.
 * Immutable, value-based. Internal domain model.
 */
public record VisualCapabilityId(String value) {
    public VisualCapabilityId {
        Objects.requireNonNull(value, "VisualCapabilityId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("VisualCapabilityId.value must not be blank");
    }
}
