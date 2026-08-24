package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Canonical runtime evidence for the resources of one exact physical-host incarnation. */
public record HostResourceSnapshot(
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        HostResourceSnapshotGeneration snapshotGeneration,
        Instant capturedAt,
        HostResourceSnapshotSchemaVersion schemaVersion,
        CapacitySnapshot staticCapacity,
        ObservedUsage observedUsage,
        Optional<WorkerRuntimeReporterRef> reportingRuntime) {

    public HostResourceSnapshot {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(snapshotGeneration, "snapshotGeneration");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(staticCapacity, "staticCapacity");
        Objects.requireNonNull(observedUsage, "observedUsage");
        reportingRuntime = Objects.requireNonNull(reportingRuntime, "reportingRuntime");

        reportingRuntime.ifPresent(reporter -> {
            if (!physicalHostId.equals(reporter.physicalHostId())
                    || !physicalHostIncarnationId.equals(reporter.physicalHostIncarnationId())) {
                throw new IllegalArgumentException(
                        "reporting runtime must bind the snapshot's exact physical-host incarnation");
            }
        });

        if (!staticCapacity.deviceResources().keySet().containsAll(observedUsage.deviceUsage().keySet())) {
            throw new IllegalArgumentException(
                    "observed device usage references a DeviceId absent from host static capacity");
        }
    }
}
