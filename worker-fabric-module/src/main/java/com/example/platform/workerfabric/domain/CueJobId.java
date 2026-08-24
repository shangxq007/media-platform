package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Opaque OpenCue submission identity; never a platform task identity. */
public record CueJobId(String value) {

    public CueJobId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CueJobId must not be blank");
        }
    }
}
