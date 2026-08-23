package com.example.platform.workerfabric.domain;

/** Typed static locality label for a physical host. */
public record HostLocation(String value) {

    public HostLocation {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HostLocation must not be blank");
        }
    }

    public static HostLocation of(String value) {
        return new HostLocation(value);
    }
}
