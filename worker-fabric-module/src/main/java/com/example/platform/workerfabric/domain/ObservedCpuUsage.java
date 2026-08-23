package com.example.platform.workerfabric.domain;

/** CPU utilization evidence; it is never a capacity or reservation unit. */
public record ObservedCpuUsage(double utilizationRatio) {

    public ObservedCpuUsage {
        requireRatio(utilizationRatio, "CPU utilization ratio");
    }

    static void requireRatio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between zero and one");
        }
    }
}
