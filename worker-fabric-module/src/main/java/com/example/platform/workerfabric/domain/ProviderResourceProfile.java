package com.example.platform.workerfabric.domain;

import java.util.Map;
import java.util.Objects;

/**
 * A provider's declared resource footprint for one task execution.
 *
 * <p><b>Distinct from {@link ProviderHardwareRequirement}</b>, which describes capability needs (CPU
 * architecture, build/codec features, sandbox permissions). This type describes <em>how much</em> the
 * runtime consumes: CPU millicores, memory bytes, temporary-storage bytes and per-device demands. It
 * is also not static host capacity — capacity comes from the host resource snapshot.
 *
 * <p>Declared by the provider contribution; the platform never invents these numbers. An absent
 * profile fails closed in the demand derivation rather than being defaulted.
 */
public record ProviderResourceProfile(
        long cpuMillicores,
        long memoryBytes,
        long temporaryStorageBytes,
        Map<DeviceId, DeviceDemand> deviceDemands) {

    public ProviderResourceProfile {
        requireNonNegative(cpuMillicores, "cpuMillicores");
        requireNonNegative(memoryBytes, "memoryBytes");
        requireNonNegative(temporaryStorageBytes, "temporaryStorageBytes");
        deviceDemands = Map.copyOf(Objects.requireNonNull(deviceDemands, "deviceDemands"));
        deviceDemands.forEach((deviceId, demand) -> {
            Objects.requireNonNull(deviceId, "device demand key");
            Objects.requireNonNull(demand, "device demand");
        });
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
