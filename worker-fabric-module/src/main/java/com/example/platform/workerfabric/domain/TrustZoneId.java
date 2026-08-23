package com.example.platform.workerfabric.domain;

/** Typed trust-zone identity for physical-host placement boundaries. */
public record TrustZoneId(String value) {

    public TrustZoneId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TrustZoneId must not be blank");
        }
    }

    public static TrustZoneId of(String value) {
        return new TrustZoneId(value);
    }
}
