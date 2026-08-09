package com.example.platform.extension.runtime;

import java.util.Objects;

/**
 * Execution resource requirements (frozen PRV2-ADR-013).
 *
 * <p>ResourceRequirements describe what an execution NEEDS. They are NOT a
 * scheduling/allocation authority, and they are NOT tenant product quota
 * (AR-PRV2-15). GPU is expressed as a requirement here — never as an
 * {@link ExecutionMode}.</p>
 *
 * @param maxMemoryMb   maximum memory in MB (>= 0)
 * @param maxCpuPercent maximum CPU percent (0-100, 0 = unbounded)
 * @param timeoutMs     execution timeout in milliseconds (>= 0)
 * @param gpuRequired   whether a GPU device is required
 * @param scratchDiskMb required scratch disk in MB (>= 0)
 */
public record ResourceRequirements(
        int maxMemoryMb,
        int maxCpuPercent,
        long timeoutMs,
        boolean gpuRequired,
        long scratchDiskMb) {

    public ResourceRequirements {
        if (maxMemoryMb < 0) {
            throw new IllegalArgumentException("maxMemoryMb must not be negative");
        }
        if (maxCpuPercent < 0 || maxCpuPercent > 100) {
            throw new IllegalArgumentException("maxCpuPercent must be within 0-100");
        }
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must not be negative");
        }
        if (scratchDiskMb < 0) {
            throw new IllegalArgumentException("scratchDiskMb must not be negative");
        }
    }

    public static ResourceRequirements defaults() {
        return new ResourceRequirements(256, 50, 30_000, false, 0);
    }
}
