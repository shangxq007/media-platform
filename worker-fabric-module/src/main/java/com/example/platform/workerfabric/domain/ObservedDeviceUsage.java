package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Provider-neutral device telemetry evidence. */
public record ObservedDeviceUsage(
        DeviceId deviceId,
        double computeUtilizationRatio,
        long vramUsedBytes,
        double encoderUtilizationRatio,
        double decoderUtilizationRatio) {

    public ObservedDeviceUsage {
        Objects.requireNonNull(deviceId, "deviceId");
        ObservedCpuUsage.requireRatio(computeUtilizationRatio, "device compute utilization ratio");
        if (vramUsedBytes < 0) {
            throw new IllegalArgumentException("observed device VRAM use must not be negative");
        }
        ObservedCpuUsage.requireRatio(encoderUtilizationRatio, "encoder utilization ratio");
        ObservedCpuUsage.requireRatio(decoderUtilizationRatio, "decoder utilization ratio");
    }
}
