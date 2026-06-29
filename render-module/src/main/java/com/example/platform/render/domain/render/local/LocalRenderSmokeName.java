package com.example.platform.render.domain.render.local;

import java.util.Objects;

/**
 * Human-readable name for a local render smoke scenario.
 */
public record LocalRenderSmokeName(String value) {
    public LocalRenderSmokeName {
        Objects.requireNonNull(value, "LocalRenderSmokeName must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("LocalRenderSmokeName must not be blank");
    }
}
