package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Concrete Native Pull placement. ASSIGNMENT_NEVER_REBINDS_PROVIDER_V1: provider identity is
 * deliberately absent and remains authoritative on the ExecutableTask/ETG.
 */
public record ExecutionAssignment(
        ExecutionAssignmentId id,
        ExecutableTaskId executableTaskId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        Set<DeviceId> deviceIds,
        Set<ReservationId> reservationIds) {

    public ExecutionAssignment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        deviceIds = canonicalSet(deviceIds, Comparator.comparing(DeviceId::value), "deviceIds");
        reservationIds = canonicalSet(
                reservationIds, Comparator.comparing(ReservationId::value), "reservationIds");
        if (reservationIds.isEmpty()) {
            throw new IllegalArgumentException("execution assignment requires a reservation");
        }
    }

    private static <T> Set<T> canonicalSet(
            Set<T> source, Comparator<T> comparator, String name) {
        Objects.requireNonNull(source, name);
        if (source.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(name + " element");
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(
                source.stream().sorted(comparator).toList()));
    }
}
