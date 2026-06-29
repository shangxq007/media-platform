package com.example.platform.render.domain.render.local;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier for a local render execution bridged from a BasicRenderPlan.
 */
public record LocalRenderExecutionId(String value) {
    public LocalRenderExecutionId {
        Objects.requireNonNull(value, "LocalRenderExecutionId must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("LocalRenderExecutionId must not be blank");
    }

    public static LocalRenderExecutionId generate() {
        return new LocalRenderExecutionId("exec-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
