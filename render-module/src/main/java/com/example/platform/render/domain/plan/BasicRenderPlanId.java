package com.example.platform.render.domain.plan;

import java.util.Objects;

/**
 * Typed identifier for a basic render plan.
 * Immutable. Internal domain model.
 */
public record BasicRenderPlanId(String value) {
    public BasicRenderPlanId {
        Objects.requireNonNull(value, "BasicRenderPlanId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BasicRenderPlanId.value must not be blank");
    }
}
