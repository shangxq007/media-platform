package com.example.platform.render.domain.transition;

import java.util.Objects;

/**
 * Typed identifier for a baseline transition planning request.
 * Immutable. Internal domain model.
 */
public record BaselineTransitionPlanningRequestId(String value) {
    public BaselineTransitionPlanningRequestId {
        Objects.requireNonNull(value, "BaselineTransitionPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BaselineTransitionPlanningRequestId.value must not be blank");
    }
}
