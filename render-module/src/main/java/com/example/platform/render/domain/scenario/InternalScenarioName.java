package com.example.platform.render.domain.scenario;

import java.util.Objects;

/**
 * Human-readable name for an internal scenario.
 * Immutable. Internal domain model.
 */
public record InternalScenarioName(String value) {
    public InternalScenarioName {
        Objects.requireNonNull(value, "InternalScenarioName.value");
        if (value.isBlank()) throw new IllegalArgumentException("InternalScenarioName.value must not be blank");
    }
}
