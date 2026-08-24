package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Durable identity of one placement-authority selection lifecycle. */
public record ExecutionBackendSelectionId(String value) {

    public ExecutionBackendSelectionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionBackendSelectionId must not be blank");
        }
    }
}
