package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Stable identity of a device attached to a physical host. */
public record DeviceId(String value) implements Serializable {

    public DeviceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeviceId must not be blank");
        }
    }

    public static DeviceId of(String value) {
        return new DeviceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
