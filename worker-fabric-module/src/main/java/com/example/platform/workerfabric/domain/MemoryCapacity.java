package com.example.platform.workerfabric.domain;

/** Static or schedulable memory capacity expressed in bytes. */
public record MemoryCapacity(long bytes) {

    public MemoryCapacity {
        if (bytes < 0) {
            throw new IllegalArgumentException("memory capacity must not be negative");
        }
    }

    public static MemoryCapacity ofBytes(long bytes) {
        return new MemoryCapacity(bytes);
    }
}
