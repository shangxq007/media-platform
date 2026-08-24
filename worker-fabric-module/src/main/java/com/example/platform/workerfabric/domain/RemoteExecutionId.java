package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Opaque execution identity assigned by a remote provider. */
public record RemoteExecutionId(String value) {

    public RemoteExecutionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RemoteExecutionId must not be blank");
        }
    }
}
