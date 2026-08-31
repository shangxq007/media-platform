package com.example.platform.workerfabric.application;

import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.WorkerRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerRuntimeDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable worker-runtime read snapshot.
 *
 * <p>Empty descriptor, support, or freshness values mean that the current persistence authority
 * cannot recover that canonical fact; absence is never promoted to a default.
 */
public record WorkerRuntimeReadSnapshot(
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        Instant registeredAt,
        Instant validUntil,
        Optional<WorkerRuntimeDescriptor> descriptor,
        WorkerRuntimeAvailability availability,
        Instant availabilityObservedAt,
        Optional<Instant> availabilityFreshUntil,
        Optional<WorkerRuntimeSupportAdvertisement> supportAdvertisement,
        Optional<HostResourceSnapshotGeneration> hostResourceSnapshotGeneration,
        List<AssignmentReadSnapshot> assignments) {

    public WorkerRuntimeReadSnapshot {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(validUntil, "validUntil");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(availabilityObservedAt, "availabilityObservedAt");
        availabilityFreshUntil = Objects.requireNonNull(
                availabilityFreshUntil, "availabilityFreshUntil");
        supportAdvertisement = Objects.requireNonNull(
                supportAdvertisement, "supportAdvertisement");
        hostResourceSnapshotGeneration = Objects.requireNonNull(
                hostResourceSnapshotGeneration, "hostResourceSnapshotGeneration");
        assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments"));
        if (!validUntil.isAfter(registeredAt)
                || !workerRuntimeId.equals(availability.workerRuntimeId())
                || !workerRuntimeIncarnationId.equals(availability.incarnationId())
                || assignments.stream().anyMatch(value -> value == null
                        || !workerRuntimeId.equals(value.assignment().workerRuntimeId())
                        || !workerRuntimeIncarnationId.equals(
                                value.assignment().workerRuntimeIncarnationId()))) {
            throw new IllegalArgumentException("worker runtime snapshot has mismatched owner facts");
        }
        descriptor.ifPresent(value -> {
            if (!workerRuntimeId.equals(value.id())) {
                throw new IllegalArgumentException("worker descriptor must bind the exact runtime");
            }
        });
        supportAdvertisement.ifPresent(value -> {
            if (!workerRuntimeId.equals(value.runtimeId())) {
                throw new IllegalArgumentException("support advertisement must bind the exact runtime");
            }
        });
    }
}
