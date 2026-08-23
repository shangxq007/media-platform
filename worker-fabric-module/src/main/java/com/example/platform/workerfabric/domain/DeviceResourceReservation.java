package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Device quantities committed by the reservation authority. */
public record DeviceResourceReservation(
        DeviceId deviceId,
        long vramBytes,
        long computeUnits,
        long encoderEngines,
        long decoderEngines) {

    public DeviceResourceReservation {
        Objects.requireNonNull(deviceId, "deviceId");
        requireNonNegative(vramBytes, "reserved device VRAM");
        requireNonNegative(computeUnits, "reserved device compute");
        requireNonNegative(encoderEngines, "reserved encoder engines");
        requireNonNegative(decoderEngines, "reserved decoder engines");
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
