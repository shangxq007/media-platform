package com.example.platform.render.domain.scenario;

import java.util.Objects;

/**
 * Typed identifier for an internal scenario.
 * Immutable. Internal domain model.
 */
public record InternalScenarioId(String value) {
    public InternalScenarioId {
        Objects.requireNonNull(value, "InternalScenarioId.value");
        if (value.isBlank()) throw new IllegalArgumentException("InternalScenarioId.value must not be blank");
    }
}
