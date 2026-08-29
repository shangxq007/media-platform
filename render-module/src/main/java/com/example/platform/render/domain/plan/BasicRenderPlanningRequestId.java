package com.example.platform.render.domain.plan;

import java.util.Objects;

/**
 * Typed identifier for a render planning request.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanningRequestId(String value) {
    public BasicRenderPlanningRequestId {
        Objects.requireNonNull(value, "BasicRenderPlanningRequestId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BasicRenderPlanningRequestId.value must not be blank");
    }
}
