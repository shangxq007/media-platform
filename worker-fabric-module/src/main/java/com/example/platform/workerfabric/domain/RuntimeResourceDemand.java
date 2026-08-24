package com.example.platform.workerfabric.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded projection of already-frozen required resources into current runtime dimensions.
 *
 * <p>It is eligibility input only and does not redefine #21 requirements or #16 capability
 * semantics.
 */
public record RuntimeResourceDemand(
        long cpuMillicores,
        long memoryBytes,
        long temporaryStorageBytes,
        Map<DeviceId, DeviceDemand> deviceDemands) {

    public RuntimeResourceDemand {
        requireNonNegative(cpuMillicores, "cpuMillicores");
        requireNonNegative(memoryBytes, "memoryBytes");
        requireNonNegative(temporaryStorageBytes, "temporaryStorageBytes");
        Objects.requireNonNull(deviceDemands, "deviceDemands");
        LinkedHashMap<DeviceId, DeviceDemand> canonical = new LinkedHashMap<>();
        deviceDemands.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().value()))
                .forEach(entry -> {
                    DeviceId deviceId = Objects.requireNonNull(entry.getKey(), "device demand key");
                    DeviceDemand demand = Objects.requireNonNull(entry.getValue(), "device demand");
                    if (!deviceId.equals(demand.deviceId())) {
                        throw new IllegalArgumentException("device demand key must match its DeviceId");
                    }
                    canonical.put(deviceId, demand);
                });
        deviceDemands = java.util.Collections.unmodifiableMap(canonical);
    }

    public static RuntimeResourceDemand none() {
        return new RuntimeResourceDemand(0, 0, 0, Map.of());
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    /** Current resource dimensions required from one exact device candidate. */
    public record DeviceDemand(
            DeviceId deviceId,
            long vramBytes,
            long computeUnits,
            long encoderEngines,
            long decoderEngines) {

        public DeviceDemand {
            Objects.requireNonNull(deviceId, "deviceId");
            requireNonNegative(vramBytes, "device vramBytes");
            requireNonNegative(computeUnits, "device computeUnits");
            requireNonNegative(encoderEngines, "device encoderEngines");
            requireNonNegative(decoderEngines, "device decoderEngines");
        }
    }
}
