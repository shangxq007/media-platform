package com.example.platform.workerfabric.domain;

/** Typed vendor label carried as descriptor data, never as device identity. */
public record DeviceVendor(String value) {

    public DeviceVendor {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeviceVendor must not be blank");
        }
    }

    public static DeviceVendor of(String value) {
        return new DeviceVendor(value);
    }
}
