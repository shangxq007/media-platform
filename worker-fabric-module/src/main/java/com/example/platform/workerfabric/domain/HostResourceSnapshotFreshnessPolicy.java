package com.example.platform.workerfabric.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Runtime/configured timeout policy; it is not canonical task or graph semantics. */
public final class HostResourceSnapshotFreshnessPolicy {

    private final Duration maximumAge;
    private final HostResourceSnapshotSchemaVersion supportedSchemaVersion;

    public HostResourceSnapshotFreshnessPolicy(
            Duration maximumAge,
            HostResourceSnapshotSchemaVersion supportedSchemaVersion) {
        this.maximumAge = Objects.requireNonNull(maximumAge, "maximumAge");
        this.supportedSchemaVersion =
                Objects.requireNonNull(supportedSchemaVersion, "supportedSchemaVersion");
        if (maximumAge.isZero() || maximumAge.isNegative()) {
            throw new IllegalArgumentException("host resource snapshot maximum age must be positive");
        }
    }

    public HostResourceSnapshotFreshness assess(
            Optional<HostResourceSnapshot> candidate,
            PhysicalHostAvailability currentHost,
            Instant evaluatedAt) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(currentHost, "currentHost");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");

        if (candidate.isEmpty()) {
            return result(HostResourceSnapshotFreshnessStatus.FAIL_CLOSED);
        }

        HostResourceSnapshot snapshot = candidate.orElseThrow();
        if (!currentHost.matchesCurrentIncarnation(
                snapshot.physicalHostId(), snapshot.physicalHostIncarnationId())) {
            return result(HostResourceSnapshotFreshnessStatus.FAIL_CLOSED);
        }
        if (!currentHost.isReachable()) {
            return result(HostResourceSnapshotFreshnessStatus.NO_ASSIGNMENT);
        }
        if (!supportedSchemaVersion.equals(snapshot.schemaVersion())) {
            return result(HostResourceSnapshotFreshnessStatus.REPROBE_REQUIRED);
        }
        if (snapshot.capturedAt().isAfter(evaluatedAt)) {
            return result(HostResourceSnapshotFreshnessStatus.FAIL_CLOSED);
        }
        if (Duration.between(snapshot.capturedAt(), evaluatedAt).compareTo(maximumAge) > 0) {
            return result(HostResourceSnapshotFreshnessStatus.REPROBE_REQUIRED);
        }
        return result(HostResourceSnapshotFreshnessStatus.FRESH);
    }

    public Duration maximumAge() {
        return maximumAge;
    }

    private static HostResourceSnapshotFreshness result(
            HostResourceSnapshotFreshnessStatus status) {
        return new HostResourceSnapshotFreshness(status);
    }
}
