package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.List;
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

    /**
     * Minimal authoritative read of the current active host registration facts.
     *
     * <p>Additive read model for the Native Pull admission bridge. Returns only persisted facts; it
     * never synthesises a {@link HostResourceSnapshot}.
     */
    default Optional<PersistedHostRegistration> findActiveHostRegistration(
            PhysicalHostId physicalHostId) {
        return Optional.empty();
    }

    /**
     * Bounded-V1 single-active-host read: the one active host registration, if any.
     *
     * <p>Bounded V1 runs exactly one local host; this overload exists so the admission bridge can
     * resolve the current authority without inventing an identity.
     */
    default Optional<PersistedHostRegistration> findActiveHostRegistration() {
        return Optional.empty();
    }

    /** Minimal authoritative read of the current active runtime registration facts. */
    default Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration(
            WorkerRuntimeId workerRuntimeId) {
        return Optional.empty();
    }

    /** Bounded-V1 single-active-runtime read: the one active runtime registration, if any. */
    default Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration() {
        return Optional.empty();
    }

    /**
     * Minimal authoritative read of one persisted host resource snapshot (static capacity, safety
     * headroom, and — when observed — runtime observed usage).
     *
     * <p>A missing observed-usage projection is a real state and is returned as
     * {@link Optional#empty()}; it must never be read as zero usage. The host descriptor is a
     * separate authority ({@link #findHostDescriptor(PhysicalHostId)}) and is never synthesised here.
     */
    default Optional<PersistedHostSnapshot> findHostSnapshot(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration) {
        return Optional.empty();
    }

    /**
     * Persists the authoritative static host descriptor produced by the registration producer.
     *
     * <p>Additive: implementations that persist the descriptor override this. An implementation that
     * does not persist it leaves {@link #findHostDescriptor(PhysicalHostId)} empty, so admission
     * fails closed rather than fabricating a descriptor.
     */
    default void registerHostDescriptor(PhysicalHostDescriptor descriptor) {
        // additive no-op; durable implementations override
    }

    /**
     * Minimal authoritative read of the persisted static host descriptor (location, trust zone and
     * device inventory). Returns only persisted facts; never synthesises a descriptor.
     */
    default Optional<PersistedHostDescriptor> findHostDescriptor(PhysicalHostId physicalHostId) {
        return Optional.empty();
    }

    /**
     * Persists the WORKER_READINESS_EVIDENCE_V1 record for the current host incarnation.
     *
     * <p>Additive: implementations that persist readiness override this. An implementation that does
     * not persist it leaves {@link #findReadiness} empty, so admission fails closed.
     */
    default void saveReadiness(WorkerReadinessEvidence evidence) {
        // additive no-op; durable implementations override
    }

    /**
     * Reads the current, non-expired readiness evidence for the exact host incarnation.
     *
     * <p>Returns {@link Optional#empty()} when no record exists, when the record has expired, or when
     * the persisted identity does not match the requested incarnation — the caller must fail closed.
     */
    default Optional<WorkerReadinessEvidence> findReadiness(
            PhysicalHostId physicalHostId, PhysicalHostIncarnationId physicalHostIncarnationId) {
        return Optional.empty();
    }

    /** Persisted active host registration facts (no snapshot reconstruction). */
    record PersistedHostRegistration(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            Instant registeredAt,
            Instant validUntil) {

        public PersistedHostRegistration {
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(registeredAt, "registeredAt");
            Objects.requireNonNull(validUntil, "validUntil");
        }
    }

    /** Persisted active runtime registration facts. */
    record PersistedRuntimeRegistration(
            WorkerRuntimeId workerRuntimeId,
            WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            Instant registeredAt,
            Instant validUntil) {

        public PersistedRuntimeRegistration {
            Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
            Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(registeredAt, "registeredAt");
            Objects.requireNonNull(validUntil, "validUntil");
        }
    }

    /** Persisted host snapshot facts: static capacity + safety headroom + optional observed usage. */
    record PersistedHostSnapshot(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration,
            String snapshotFingerprint,
            Instant capturedAt,
            int schemaVersion,
            long cpuMillicores,
            long memoryBytes,
            long temporaryStorageBytes,
            long safetyHeadroomCpuMillicores,
            long safetyHeadroomMemoryBytes,
            long safetyHeadroomTemporaryStorageBytes,
            List<PersistedSnapshotDevice> devices,
            Optional<PersistedObservedUsage> observedUsage) {

        public PersistedHostSnapshot {
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
            Objects.requireNonNull(snapshotGeneration, "snapshotGeneration");
            Objects.requireNonNull(snapshotFingerprint, "snapshotFingerprint");
            Objects.requireNonNull(capturedAt, "capturedAt");
            devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
            observedUsage = Objects.requireNonNull(observedUsage, "observedUsage");
        }
    }

    /** Persisted host-level observed usage. Present only when the row carries a complete observation. */
    record PersistedObservedUsage(
            double cpuUtilizationRatio,
            long memoryUsedBytes,
            long temporaryStorageUsedBytes,
            List<PersistedObservedDeviceUsage> devices) {

        public PersistedObservedUsage {
            devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
        }
    }

    /** Persisted per-device observed usage. */
    record PersistedObservedDeviceUsage(
            String deviceId,
            double computeUtilizationRatio,
            long vramUsedBytes,
            double encoderUtilizationRatio,
            double decoderUtilizationRatio) {

        public PersistedObservedDeviceUsage {
            Objects.requireNonNull(deviceId, "deviceId");
        }
    }

    /** Persisted static host descriptor facts (location, trust zone, device inventory). */
    record PersistedHostDescriptor(
            PhysicalHostId physicalHostId,
            HostLocation location,
            TrustZoneId trustZoneId,
            Instant capturedAt,
            List<PersistedDescriptorDevice> devices) {

        public PersistedHostDescriptor {
            Objects.requireNonNull(physicalHostId, "physicalHostId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(trustZoneId, "trustZoneId");
            Objects.requireNonNull(capturedAt, "capturedAt");
            devices = List.copyOf(Objects.requireNonNull(devices, "devices"));
        }
    }

    /** Persisted static device descriptor facts. */
    record PersistedDescriptorDevice(
            String deviceId, DeviceKind kind, String vendor, String model) {

        public PersistedDescriptorDevice {
            Objects.requireNonNull(deviceId, "deviceId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(vendor, "vendor");
            Objects.requireNonNull(model, "model");
        }
    }

    /** Persisted per-device static capacity and safety headroom. */
    record PersistedSnapshotDevice(
            String deviceId,
            long vramBytes,
            long computeUnits,
            long encoderEngines,
            long decoderEngines,
            long safetyHeadroomVramBytes,
            long safetyHeadroomComputeUnits,
            long safetyHeadroomEncoderEngines,
            long safetyHeadroomDecoderEngines) {

        public PersistedSnapshotDevice {
            Objects.requireNonNull(deviceId, "deviceId");
        }
    }

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
