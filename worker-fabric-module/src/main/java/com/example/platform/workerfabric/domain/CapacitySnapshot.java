package com.example.platform.workerfabric.domain;

import java.util.Map;
import java.util.Objects;

/** Immutable static/fingerprinted capacity composition for one physical host. */
public record CapacitySnapshot(
        CpuCapacity cpu,
        MemoryCapacity memory,
        TemporaryStorageCapacity temporaryStorage,
        Map<DeviceId, DeviceResourceCapacity> deviceResources) {

    public CapacitySnapshot {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(temporaryStorage, "temporaryStorage");
        deviceResources = Map.copyOf(Objects.requireNonNull(deviceResources, "deviceResources"));
        deviceResources.forEach((deviceId, capacity) -> {
            Objects.requireNonNull(deviceId, "device resource key");
            Objects.requireNonNull(capacity, "device resource capacity");
            if (!deviceId.equals(capacity.deviceId())) {
                throw new IllegalArgumentException("device resource key must match its DeviceId");
            }
        });
    }
}
