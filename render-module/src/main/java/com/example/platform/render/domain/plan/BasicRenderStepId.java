package com.example.platform.render.domain.plan;

import java.util.Objects;

/**
 * Typed identifier for a render step.
 * Immutable. Internal domain model.
 */
public record BasicRenderStepId(String value) {
    public BasicRenderStepId {
        Objects.requireNonNull(value, "BasicRenderStepId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BasicRenderStepId.value must not be blank");
    }
}
