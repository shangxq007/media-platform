package com.example.platform.render.domain.effect;

import java.util.Objects;

/**
 * Typed identifier for a baseline effect planning request.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanningRequestId(String value) {
    public BaselineEffectPlanningRequestId {
        Objects.requireNonNull(value, "BaselineEffectPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineEffectPlanningRequestId.value must not be blank");
    }
}
