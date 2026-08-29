package com.example.platform.render.domain.transition;

import java.util.Objects;

/**
 * Typed identifier for a baseline transition plan.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanId(String value) {
    public BaselineTransitionPlanId {
        Objects.requireNonNull(value, "BaselineTransitionPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineTransitionPlanId.value must not be blank");
    }
}
