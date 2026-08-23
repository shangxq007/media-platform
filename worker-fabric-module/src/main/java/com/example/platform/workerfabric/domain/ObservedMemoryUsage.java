package com.example.platform.workerfabric.domain;

/** Observed memory-use evidence in bytes. */
public record ObservedMemoryUsage(long usedBytes) {

    public ObservedMemoryUsage {
        if (usedBytes < 0) {
            throw new IllegalArgumentException("observed memory use must not be negative");
        }
    }
}
