package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of a physical host across restarts and re-registration. */
public record PhysicalHostId(String value) implements Serializable {

    public PhysicalHostId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PhysicalHostId must not be blank");
        }
    }

    public static PhysicalHostId of(String value) {
        return new PhysicalHostId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
