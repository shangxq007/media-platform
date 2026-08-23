package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of one mutable resource reservation. */
public record ReservationId(String value) implements Serializable {

    public ReservationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ReservationId must not be blank");
        }
    }

    public static ReservationId of(String value) {
        return new ReservationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
