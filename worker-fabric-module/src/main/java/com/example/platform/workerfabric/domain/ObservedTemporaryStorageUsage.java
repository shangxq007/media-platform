package com.example.platform.workerfabric.domain;

/** Observed temporary-storage-use evidence in bytes. */
public record ObservedTemporaryStorageUsage(long usedBytes) {

    public ObservedTemporaryStorageUsage {
        if (usedBytes < 0) {
            throw new IllegalArgumentException("observed temporary-storage use must not be negative");
        }
    }
}
