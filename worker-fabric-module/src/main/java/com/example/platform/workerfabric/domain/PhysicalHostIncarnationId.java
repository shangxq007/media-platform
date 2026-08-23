package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Identity of one boot or registration incarnation of a physical host. */
public record PhysicalHostIncarnationId(String value) implements Serializable {

    public PhysicalHostIncarnationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PhysicalHostIncarnationId must not be blank");
        }
    }

    public static PhysicalHostIncarnationId of(String value) {
        return new PhysicalHostIncarnationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
