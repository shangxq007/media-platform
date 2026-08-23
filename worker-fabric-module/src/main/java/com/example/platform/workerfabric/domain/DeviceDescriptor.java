package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Immutable, provider-neutral description of a physical device. */
public record DeviceDescriptor(
        DeviceId id,
        DeviceKind kind,
        DeviceVendor vendor,
        DeviceModel model) {

    public DeviceDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(vendor, "vendor");
        Objects.requireNonNull(model, "model");
    }
}
