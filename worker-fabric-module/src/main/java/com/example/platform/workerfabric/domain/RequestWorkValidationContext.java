package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Objects;

/** Current central registration, host truth, and reservation-ledger projection. */
public record RequestWorkValidationContext(
        WorkerRuntimeDescriptor workerRuntime,
        WorkerRuntimeAvailability authoritativeWorkerRuntimeAvailability,
        LocalWorkerRuntimeIncarnationBinding runtimeHostBinding,
        PhysicalHostDescriptor physicalHost,
        PhysicalHostAvailability hostAvailability,
        HostResourceSnapshot authoritativeHostResourceSnapshot,
        HostResourceSnapshotFreshnessPolicy snapshotFreshnessPolicy,
        Instant evaluatedAt,
        SchedulableCapacity authoritativeSchedulableCapacity) {

    public RequestWorkValidationContext {
        Objects.requireNonNull(workerRuntime, "workerRuntime");
        Objects.requireNonNull(authoritativeWorkerRuntimeAvailability,
                "authoritativeWorkerRuntimeAvailability");
        Objects.requireNonNull(runtimeHostBinding, "runtimeHostBinding");
        Objects.requireNonNull(physicalHost, "physicalHost");
        Objects.requireNonNull(hostAvailability, "hostAvailability");
        Objects.requireNonNull(authoritativeHostResourceSnapshot,
                "authoritativeHostResourceSnapshot");
        Objects.requireNonNull(snapshotFreshnessPolicy, "snapshotFreshnessPolicy");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(authoritativeSchedulableCapacity,
                "authoritativeSchedulableCapacity");

        if (workerRuntime.lifecycleKind() == RuntimeLifecycleKind.REMOTE_RUNTIME
                || workerRuntime.physicalHostId().isEmpty()) {
            throw new IllegalArgumentException(
                    "RequestWork is only valid for a local Native Pull runtime");
        }
        if (!workerRuntime.id().equals(runtimeHostBinding.workerRuntimeId())
                || !workerRuntime.physicalHostId().orElseThrow().equals(physicalHost.id())
                || !physicalHost.id().equals(runtimeHostBinding.physicalHostId())) {
            throw new IllegalArgumentException(
                    "central runtime registration must bind the exact physical host");
        }
        if (!authoritativeWorkerRuntimeAvailability.matchesCurrentIncarnation(
                runtimeHostBinding.workerRuntimeId(),
                runtimeHostBinding.workerRuntimeIncarnationId())) {
            throw new IllegalArgumentException(
                    "central runtime availability must bind the registered runtime incarnation");
        }
        if (!hostAvailability.matchesCurrentIncarnation(
                runtimeHostBinding.physicalHostId(),
                runtimeHostBinding.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "central runtime registration must bind the current host incarnation");
        }
        if (!hostAvailability.matchesCurrentIncarnation(
                authoritativeHostResourceSnapshot.physicalHostId(),
                authoritativeHostResourceSnapshot.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "authoritative snapshot must bind the current host incarnation");
        }
        if (!authoritativeSchedulableCapacity.physicalHostId().equals(physicalHost.id())
                || !authoritativeSchedulableCapacity.physicalHostIncarnationId().equals(
                        hostAvailability.incarnationId())) {
            throw new IllegalArgumentException(
                    "authoritative schedulable capacity must bind the current host incarnation");
        }
    }
}
