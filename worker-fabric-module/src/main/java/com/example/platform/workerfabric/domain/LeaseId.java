package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of one Native Pull lease. */
public record LeaseId(String value) implements Serializable {

    public LeaseId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LeaseId must not be blank");
        }
    }

    public static LeaseId of(String value) {
        return new LeaseId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
