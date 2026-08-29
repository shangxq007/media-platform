package com.example.platform.render.domain.effect;

import java.util.Objects;

/**
 * Typed identifier for a baseline effect plan.
 * Immutable. Internal domain model.
 */
public record BaselineEffectPlanId(String value) {
    public BaselineEffectPlanId {
        Objects.requireNonNull(value, "BaselineEffectPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineEffectPlanId.value must not be blank");
    }
}
