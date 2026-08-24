package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Durable registration authority that must be established before Native Pull RequestWork. */
public interface WorkerFabricRegistrationBoundary {

    void registerHost(HostRegistration registration);

    void registerRuntime(RuntimeRegistration registration);

    /** Reloads the durable per-host-incarnation generation authority after process restart. */
    Optional<HostResourceSnapshotGeneration> currentSnapshotGeneration(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId);

    record HostRegistration(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshot hostResourceSnapshot,
            SafetyHeadroom safetyHeadroom,
            Instant registeredAt,
            Instant validUntil) {

        public HostRegistration {
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(hostResourceSnapshot, "hostResourceSnapshot");
            Objects.requireNonNull(safetyHeadroom, "safetyHeadroom");
            Objects.requireNonNull(registeredAt, "registeredAt");
            Objects.requireNonNull(validUntil, "validUntil");
            if (!physicalHostId.equals(hostResourceSnapshot.physicalHostId())
                    || !physicalHostIncarnationId.equals(
                            hostResourceSnapshot.physicalHostIncarnationId())) {
                throw new IllegalArgumentException(
                        "host registration must bind the exact resource-snapshot incarnation");
            }
            if (!validUntil.isAfter(registeredAt)) {
                throw new IllegalArgumentException("host registration must expire after registration");
            }
            ReservedResources withheld = safetyHeadroom.resources();
            CapacitySnapshot capacity = hostResourceSnapshot.staticCapacity();
            if (withheld.cpuMillicores() > capacity.cpu().millicores()
                    || withheld.memoryBytes() > capacity.memory().bytes()
                    || withheld.temporaryStorageBytes() > capacity.temporaryStorage().bytes()) {
                throw new IllegalArgumentException(
                        "host registration safety headroom exceeds static host capacity");
            }
            withheld.deviceResources().forEach((deviceId, reservation) -> {
                DeviceResourceCapacity device = capacity.deviceResources().get(deviceId);
                if (device == null
                        || reservation.vramBytes() > device.vramBytes()
                        || reservation.computeUnits() > device.computeUnits()
                        || reservation.encoderEngines() > device.encoderEngines()
                        || reservation.decoderEngines() > device.decoderEngines()) {
                    throw new IllegalArgumentException(
                            "host registration safety headroom exceeds static device capacity");
                }
            });
        }
    }

    record RuntimeRegistration(
            WorkerRuntimeId workerRuntimeId,
            WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            Instant registeredAt,
            Instant validUntil) {

        public RuntimeRegistration {
            Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
            Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(registeredAt, "registeredAt");
            Objects.requireNonNull(validUntil, "validUntil");
            if (!validUntil.isAfter(registeredAt)) {
                throw new IllegalArgumentException(
                        "runtime registration must expire after registration");
            }
        }
    }
}
