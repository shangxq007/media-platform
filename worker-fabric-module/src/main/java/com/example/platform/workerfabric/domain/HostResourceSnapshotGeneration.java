package com.example.platform.workerfabric.domain;

/** Monotonic resource-evidence generation within one physical-host incarnation. */
public record HostResourceSnapshotGeneration(long value)
        implements Comparable<HostResourceSnapshotGeneration> {

    public HostResourceSnapshotGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("host resource snapshot generation must be positive");
        }
    }

    public static HostResourceSnapshotGeneration first() {
        return new HostResourceSnapshotGeneration(1);
    }

    public HostResourceSnapshotGeneration next() {
        return new HostResourceSnapshotGeneration(Math.incrementExact(value));
    }

    @Override
    public int compareTo(HostResourceSnapshotGeneration other) {
        return Long.compare(value, other.value);
    }
}
