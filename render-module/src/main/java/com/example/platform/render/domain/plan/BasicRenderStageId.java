package com.example.platform.render.domain.plan;

import java.util.Objects;

/**
 * Typed identifier for a render stage.
 * Immutable. Internal domain model.
 */
public record BasicRenderStageId(String value) {
    public BasicRenderStageId {
        Objects.requireNonNull(value, "BasicRenderStageId.value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("BasicRenderStageId.value must not be blank");
    }
}
