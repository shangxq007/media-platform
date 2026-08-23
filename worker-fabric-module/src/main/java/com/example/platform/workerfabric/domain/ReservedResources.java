package com.example.platform.workerfabric.domain;

import java.util.Map;
import java.util.Objects;

/** Quantities committed by one reservation; distinct from static capacity and telemetry. */
public record ReservedResources(
        long cpuMillicores,
        long memoryBytes,
        long temporaryStorageBytes,
        Map<DeviceId, DeviceResourceReservation> deviceResources) {

    public ReservedResources {
        requireNonNegative(cpuMillicores, "reserved CPU");
        requireNonNegative(memoryBytes, "reserved memory");
        requireNonNegative(temporaryStorageBytes, "reserved temporary storage");
        deviceResources = Map.copyOf(Objects.requireNonNull(deviceResources, "deviceResources"));
        deviceResources.forEach((deviceId, reservation) -> {
            Objects.requireNonNull(deviceId, "device reservation key");
            Objects.requireNonNull(reservation, "device resource reservation");
            if (!deviceId.equals(reservation.deviceId())) {
                throw new IllegalArgumentException("device reservation key must match its DeviceId");
            }
        });
    }

    public static ReservedResources none() {
        return new ReservedResources(0, 0, 0, Map.of());
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
