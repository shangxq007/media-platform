package com.example.platform.workerfabric.domain;

/** Static or schedulable temporary-storage capacity expressed in bytes. */
public record TemporaryStorageCapacity(long bytes) {

    public TemporaryStorageCapacity {
        if (bytes < 0) {
            throw new IllegalArgumentException("temporary-storage capacity must not be negative");
        }
    }

    public static TemporaryStorageCapacity ofBytes(long bytes) {
        return new TemporaryStorageCapacity(bytes);
    }
}
