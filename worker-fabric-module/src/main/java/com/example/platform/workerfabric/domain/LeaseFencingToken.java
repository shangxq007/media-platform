package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Opaque token fencing all messages emitted under one exact Native Pull lease. */
public record LeaseFencingToken(String value) implements Serializable {

    public LeaseFencingToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LeaseFencingToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
