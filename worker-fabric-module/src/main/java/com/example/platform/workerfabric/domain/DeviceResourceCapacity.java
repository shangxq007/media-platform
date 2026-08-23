package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Bounded, provider-neutral device capacity dimensions. */
public record DeviceResourceCapacity(
        DeviceId deviceId,
        long vramBytes,
        long computeUnits,
        long encoderEngines,
        long decoderEngines) {

    public DeviceResourceCapacity {
        Objects.requireNonNull(deviceId, "deviceId");
        requireNonNegative(vramBytes, "device VRAM capacity");
        requireNonNegative(computeUnits, "device compute capacity");
        requireNonNegative(encoderEngines, "device encoder capacity");
        requireNonNegative(decoderEngines, "device decoder capacity");
    }

    public static DeviceResourceCapacity none(DeviceId deviceId) {
        return new DeviceResourceCapacity(deviceId, 0, 0, 0, 0);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
