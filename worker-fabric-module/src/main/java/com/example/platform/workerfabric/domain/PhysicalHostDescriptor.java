package com.example.platform.workerfabric.domain;

import java.util.List;
import java.util.Objects;

/** Immutable static description of a physical host and its device inventory. */
public record PhysicalHostDescriptor(
        PhysicalHostId id,
        HostLocation location,
        TrustZoneId trustZoneId,
        List<DeviceDescriptor> devices) {

    public PhysicalHostDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(trustZoneId, "trustZoneId");
        devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
    }
}
