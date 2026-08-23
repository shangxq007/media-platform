package com.example.platform.workerfabric.domain;

/** Typed model label carried as descriptor data, never as device identity. */
public record DeviceModel(String value) {

    public DeviceModel {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeviceModel must not be blank");
        }
    }

    public static DeviceModel of(String value) {
        return new DeviceModel(value);
    }
}
