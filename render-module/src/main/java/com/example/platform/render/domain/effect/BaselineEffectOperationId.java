package com.example.platform.render.domain.effect;

import java.util.Objects;

/**
 * Typed identifier for a baseline effect operation.
 * Immutable. Internal domain model.
 */
public record BaselineEffectOperationId(String value) {
    public BaselineEffectOperationId {
        Objects.requireNonNull(value, "BaselineEffectOperationId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineEffectOperationId.value must not be blank");
    }
}
