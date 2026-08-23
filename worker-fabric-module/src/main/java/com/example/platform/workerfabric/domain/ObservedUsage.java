package com.example.platform.workerfabric.domain;

import java.util.Map;
import java.util.Objects;

/** Immutable telemetry projection: evidence only, never capacity or reservation authority. */
public record ObservedUsage(
        ObservedCpuUsage cpu,
        ObservedMemoryUsage memory,
        ObservedTemporaryStorageUsage temporaryStorage,
        Map<DeviceId, ObservedDeviceUsage> deviceUsage) {

    public ObservedUsage {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(temporaryStorage, "temporaryStorage");
        deviceUsage = Map.copyOf(Objects.requireNonNull(deviceUsage, "deviceUsage"));
        deviceUsage.forEach((deviceId, usage) -> {
            Objects.requireNonNull(deviceId, "observed device key");
            Objects.requireNonNull(usage, "observed device usage");
            if (!deviceId.equals(usage.deviceId())) {
                throw new IllegalArgumentException("observed device key must match its DeviceId");
            }
        });
    }
}
