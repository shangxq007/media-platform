package com.example.platform.workerfabric.application;

import com.example.platform.workerfabric.domain.DeviceAvailability;
import com.example.platform.workerfabric.domain.DeviceDescriptor;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.HostResourceSnapshotSchemaVersion;
import com.example.platform.workerfabric.domain.ObservedDeviceUsage;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One exact durable current-generation device-capacity scope and its owner references. */
public record DeviceReadSnapshot(
        DeviceId deviceId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        HostResourceSnapshotGeneration hostResourceSnapshotGeneration,
        Instant capturedAt,
        HostResourceSnapshotSchemaVersion schemaVersion,
        Optional<DeviceDescriptor> descriptor,
        Optional<DeviceAvailability> availability,
        Optional<Instant> availabilityObservedAt,
        Optional<Instant> availabilityFreshUntil,
        DeviceResourceCapacity capacity,
        Optional<ObservedDeviceUsage> observedUsage,
        Optional<Instant> observedAt,
        List<AssignmentReadSnapshot> assignments) {

    public DeviceReadSnapshot {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(hostResourceSnapshotGeneration, "hostResourceSnapshotGeneration");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        availability = Objects.requireNonNull(availability, "availability");
        availabilityObservedAt = Objects.requireNonNull(
                availabilityObservedAt, "availabilityObservedAt");
        availabilityFreshUntil = Objects.requireNonNull(
                availabilityFreshUntil, "availabilityFreshUntil");
        Objects.requireNonNull(capacity, "capacity");
        observedUsage = Objects.requireNonNull(observedUsage, "observedUsage");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments"));
        if (!deviceId.equals(capacity.deviceId())
                || descriptor.filter(value -> !deviceId.equals(value.id())).isPresent()
                || availability.filter(value -> !deviceId.equals(value.deviceId())).isPresent()
                || observedUsage.filter(value -> !deviceId.equals(value.deviceId())).isPresent()
                || observedUsage.isPresent() != observedAt.isPresent()
                || availability.isPresent() != availabilityObservedAt.isPresent()
                || assignments.stream().anyMatch(value -> value == null
                        || !physicalHostId.equals(value.assignment().physicalHostId())
                        || !physicalHostIncarnationId.equals(
                                value.assignment().physicalHostIncarnationId())
                        || !value.assignment().deviceIds().contains(deviceId))) {
            throw new IllegalArgumentException("device snapshot has mismatched owner facts");
        }
    }
}
