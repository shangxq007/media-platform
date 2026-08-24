package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Typed result of applying runtime freshness policy to one host snapshot. */
public record HostResourceSnapshotFreshness(HostResourceSnapshotFreshnessStatus status) {

    public HostResourceSnapshotFreshness {
        Objects.requireNonNull(status, "status");
    }

    public boolean permitsAssignment() {
        return status == HostResourceSnapshotFreshnessStatus.FRESH;
    }

    public boolean requiresReprobe() {
        return status == HostResourceSnapshotFreshnessStatus.REPROBE_REQUIRED;
    }
}
