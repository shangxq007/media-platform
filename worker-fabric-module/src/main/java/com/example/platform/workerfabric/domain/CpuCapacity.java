package com.example.platform.workerfabric.domain;

/** Static or schedulable CPU capacity expressed in millicores, never utilization percent. */
public record CpuCapacity(long millicores) {

    public CpuCapacity {
        if (millicores < 0) {
            throw new IllegalArgumentException("CPU capacity must not be negative");
        }
    }

    public static CpuCapacity ofMillicores(long millicores) {
        return new CpuCapacity(millicores);
    }
}
