package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Globally idempotent identity of one normalized execution observation. */
public record ObservationId(String value) {

    public ObservationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ObservationId must not be blank");
        }
    }
}
