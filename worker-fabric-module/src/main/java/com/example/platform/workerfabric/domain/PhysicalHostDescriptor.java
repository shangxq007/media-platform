package com.example.platform.workerfabric.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        Set<DeviceId> deviceIds = new HashSet<>();
        for (DeviceDescriptor device : devices) {
            if (!deviceIds.add(device.id())) {
                throw new IllegalArgumentException(
                        "physical host device inventory contains duplicate DeviceId: " + device.id());
            }
        }
    }
}
