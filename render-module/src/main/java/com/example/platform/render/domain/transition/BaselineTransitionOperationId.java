package com.example.platform.render.domain.transition;

import java.util.Objects;

/**
 * Typed identifier for a baseline transition operation.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionOperationId(String value) {
    public BaselineTransitionOperationId {
        Objects.requireNonNull(value, "BaselineTransitionOperationId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineTransitionOperationId.value must not be blank");
    }
}
