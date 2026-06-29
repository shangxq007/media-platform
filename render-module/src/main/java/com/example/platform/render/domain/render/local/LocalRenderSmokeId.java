package com.example.platform.render.domain.render.local;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier for a local render smoke execution.
 */
public record LocalRenderSmokeId(String value) {
    public LocalRenderSmokeId {
        Objects.requireNonNull(value, "LocalRenderSmokeId must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("LocalRenderSmokeId must not be blank");
    }

    public static LocalRenderSmokeId generate() {
        return new LocalRenderSmokeId("smoke-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
