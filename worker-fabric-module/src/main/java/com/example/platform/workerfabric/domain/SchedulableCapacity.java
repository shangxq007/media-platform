package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Capacity available for placement after the frozen reservation-first rule.
 *
 * <p>STATIC_CAPACITY - ACTIVE_RESERVATIONS - RECOVERY_HOLD_RESERVATIONS
 * - RESIDENT_RESERVATIONS - SAFETY_HEADROOM. Observation never replaces that arithmetic;
 * contradictory observation only fails new assignment closed for reconciliation.
 */
public record SchedulableCapacity(
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        SchedulableCapacityDisposition disposition,
        CpuCapacity cpu,
        MemoryCapacity memory,
        TemporaryStorageCapacity temporaryStorage,
        Map<DeviceId, DeviceResourceCapacity> deviceResources) {

    public SchedulableCapacity {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(temporaryStorage, "temporaryStorage");
        deviceResources = Map.copyOf(Objects.requireNonNull(deviceResources, "deviceResources"));
        deviceResources.forEach((deviceId, capacity) -> {
            Objects.requireNonNull(deviceId, "device resource key");
            Objects.requireNonNull(capacity, "device resource capacity");
            if (!deviceId.equals(capacity.deviceId())) {
                throw new IllegalArgumentException("device resource key must match its DeviceId");
            }
        });
        if (disposition != SchedulableCapacityDisposition.AVAILABLE
                && (cpu.millicores() != 0
                || memory.bytes() != 0
                || temporaryStorage.bytes() != 0
                || deviceResources.values().stream().anyMatch(SchedulableCapacity::hasCapacity))) {
            throw new IllegalArgumentException("unavailable schedulable capacity must be zero");
        }
    }

    public boolean available() {
        return disposition == SchedulableCapacityDisposition.AVAILABLE;
    }

    public static SchedulableCapacity forHost(
            HostResourceSnapshot snapshot,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostAvailability hostAvailability,
            HostResourceSnapshotFreshnessPolicy freshnessPolicy,
            Instant evaluatedAt) {
        validateHostSnapshot(snapshot, hostAvailability);
        validateReservationScope(reservations, snapshot.physicalHostId());
        Objects.requireNonNull(safetyHeadroom, "safetyHeadroom");
        HostResourceSnapshotFreshness freshness = Objects.requireNonNull(freshnessPolicy, "freshnessPolicy")
                .assess(java.util.Optional.of(snapshot), hostAvailability, evaluatedAt);
        if (!freshness.permitsAssignment()) {
            return unavailable(snapshot, disposition(freshness));
        }
        if (observationContradictsStaticCapacity(snapshot)) {
            return unavailable(snapshot, SchedulableCapacityDisposition.RECONCILIATION_REQUIRED);
        }
        return calculate(
                snapshot,
                reservations,
                safetyHeadroom,
                hostAvailability.isReachable());
    }

    public static SchedulableCapacity forLocalRuntime(
            HostResourceSnapshot snapshot,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding,
            WorkerRuntimeDescriptor runtimeDescriptor,
            HostResourceSnapshotFreshnessPolicy freshnessPolicy,
            Instant evaluatedAt) {
        validateHostSnapshot(snapshot, hostAvailability);
        validateLocalRuntimeBinding(
                snapshot, hostAvailability, runtimeAvailability, binding, runtimeDescriptor);
        validateReservationScope(reservations, snapshot.physicalHostId());
        Objects.requireNonNull(safetyHeadroom, "safetyHeadroom");
        HostResourceSnapshotFreshness freshness = Objects.requireNonNull(freshnessPolicy, "freshnessPolicy")
                .assess(java.util.Optional.of(snapshot), hostAvailability, evaluatedAt);
        if (!freshness.permitsAssignment()) {
            return unavailable(snapshot, disposition(freshness));
        }
        if (!runtimeAvailability.isReachable()) {
            return unavailable(snapshot, SchedulableCapacityDisposition.NO_ASSIGNMENT);
        }
        if (observationContradictsStaticCapacity(snapshot)) {
            return unavailable(snapshot, SchedulableCapacityDisposition.RECONCILIATION_REQUIRED);
        }
        return calculate(
                snapshot,
                reservations,
                safetyHeadroom,
                hostAvailability.isReachable() && runtimeAvailability.isReachable());
    }

    private static void validateHostSnapshot(
            HostResourceSnapshot snapshot,
            PhysicalHostAvailability hostAvailability) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(hostAvailability, "hostAvailability");
        if (!hostAvailability.matchesCurrentIncarnation(
                snapshot.physicalHostId(), snapshot.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "host resource snapshot does not match the available physical-host incarnation");
        }
    }

    private static void validateLocalRuntimeBinding(
            HostResourceSnapshot snapshot,
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding,
            WorkerRuntimeDescriptor runtimeDescriptor) {
        Objects.requireNonNull(hostAvailability, "hostAvailability");
        Objects.requireNonNull(runtimeAvailability, "runtimeAvailability");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(runtimeDescriptor, "runtimeDescriptor");

        if (runtimeDescriptor.lifecycleKind() == RuntimeLifecycleKind.REMOTE_RUNTIME) {
            throw new IllegalArgumentException(
                    "REMOTE_RUNTIME cannot participate in local runtime capacity calculation");
        }
        if (!runtimeDescriptor.id().equals(binding.workerRuntimeId())) {
            throw new IllegalArgumentException(
                    "runtime descriptor WorkerRuntimeId does not match local runtime binding");
        }
        if (!binding.workerRuntimeId().equals(runtimeAvailability.workerRuntimeId())) {
            throw new IllegalArgumentException(
                    "local runtime binding WorkerRuntimeId does not match runtime availability");
        }
        PhysicalHostId descriptorHostId = runtimeDescriptor.physicalHostId().orElseThrow(() ->
                new IllegalArgumentException("local runtime descriptor must identify a physical host"));
        if (!descriptorHostId.equals(binding.physicalHostId())) {
            throw new IllegalArgumentException(
                    "runtime descriptor PhysicalHostId does not match local runtime binding");
        }
        if (!binding.physicalHostId().equals(hostAvailability.physicalHostId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostId does not match host availability");
        }
        if (!binding.workerRuntimeIncarnationId().equals(runtimeAvailability.incarnationId())) {
            throw new IllegalArgumentException(
                    "local runtime binding WorkerRuntimeIncarnationId does not match runtime availability");
        }
        if (!binding.physicalHostIncarnationId().equals(hostAvailability.incarnationId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostIncarnationId does not match host availability");
        }
        if (!binding.physicalHostId().equals(snapshot.physicalHostId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostId does not match host resource snapshot");
        }
        if (!binding.physicalHostIncarnationId().equals(snapshot.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostIncarnationId does not match host resource snapshot");
        }
    }

    private static SchedulableCapacity calculate(
            HostResourceSnapshot snapshot,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            boolean targetReachable) {
        CapacitySnapshot staticCapacity = snapshot.staticCapacity();
        Objects.requireNonNull(staticCapacity, "staticCapacity");
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(safetyHeadroom, "safetyHeadroom");

        if (!targetReachable) {
            return unavailable(snapshot, SchedulableCapacityDisposition.NO_ASSIGNMENT);
        }

        ResourceAccumulator active = new ResourceAccumulator();
        ResourceAccumulator recoveryHold = new ResourceAccumulator();
        ResourceAccumulator resident = new ResourceAccumulator();

        for (Reservation reservation : reservations) {
            Objects.requireNonNull(reservation, "reservation");
            if (reservation.state() == ReservationState.RELEASED) {
                continue;
            }
            if (reservation.isResident()) {
                resident.add(reservation.resources());
            } else if (reservation.state() == ReservationState.RECOVERY_HOLD) {
                recoveryHold.add(reservation.resources());
            } else {
                active.add(reservation.resources());
            }
        }

        ResourceAccumulator unavailable = new ResourceAccumulator();
        unavailable.add(active);
        unavailable.add(recoveryHold);
        unavailable.add(resident);
        unavailable.add(safetyHeadroom.resources());

        if (unavailable.exceeds(staticCapacity)) {
            return unavailable(snapshot, SchedulableCapacityDisposition.RECONCILIATION_REQUIRED);
        }

        return subtract(snapshot, unavailable);
    }

    private static SchedulableCapacity subtract(
            HostResourceSnapshot snapshot,
            ResourceAccumulator unavailable) {
        CapacitySnapshot staticCapacity = snapshot.staticCapacity();
        Map<DeviceId, DeviceResourceCapacity> remainingDevices = new LinkedHashMap<>();
        staticCapacity.deviceResources().forEach((deviceId, capacity) -> {
            DeviceTotals withheld = unavailable.devices.getOrDefault(deviceId, DeviceTotals.NONE);
            remainingDevices.put(deviceId, new DeviceResourceCapacity(
                    deviceId,
                    remaining(capacity.vramBytes(), withheld.vramBytes),
                    remaining(capacity.computeUnits(), withheld.computeUnits),
                    remaining(capacity.encoderEngines(), withheld.encoderEngines),
                    remaining(capacity.decoderEngines(), withheld.decoderEngines)));
        });
        unavailable.devices.keySet().forEach(deviceId -> {
            if (!staticCapacity.deviceResources().containsKey(deviceId)) {
                throw new IllegalArgumentException(
                        "reservation or safety headroom references a device absent from static capacity: "
                                + deviceId);
            }
        });

        return new SchedulableCapacity(
                snapshot.physicalHostId(),
                snapshot.physicalHostIncarnationId(),
                SchedulableCapacityDisposition.AVAILABLE,
                CpuCapacity.ofMillicores(remaining(
                        staticCapacity.cpu().millicores(), unavailable.cpuMillicores)),
                MemoryCapacity.ofBytes(remaining(
                        staticCapacity.memory().bytes(), unavailable.memoryBytes)),
                TemporaryStorageCapacity.ofBytes(remaining(
                        staticCapacity.temporaryStorage().bytes(), unavailable.temporaryStorageBytes)),
                remainingDevices);
    }

    private static SchedulableCapacity unavailable(
            HostResourceSnapshot snapshot,
            SchedulableCapacityDisposition disposition) {
        if (disposition == SchedulableCapacityDisposition.AVAILABLE) {
            throw new IllegalArgumentException("unavailable capacity requires a fail-closed disposition");
        }
        CapacitySnapshot staticCapacity = snapshot.staticCapacity();
        Map<DeviceId, DeviceResourceCapacity> unavailableDevices = new LinkedHashMap<>();
        staticCapacity.deviceResources().keySet()
                .forEach(deviceId -> unavailableDevices.put(deviceId, DeviceResourceCapacity.none(deviceId)));
        return new SchedulableCapacity(
                snapshot.physicalHostId(),
                snapshot.physicalHostIncarnationId(),
                disposition,
                CpuCapacity.ofMillicores(0),
                MemoryCapacity.ofBytes(0),
                TemporaryStorageCapacity.ofBytes(0),
                unavailableDevices);
    }

    private static SchedulableCapacityDisposition disposition(
            HostResourceSnapshotFreshness freshness) {
        return switch (freshness.status()) {
            case FRESH -> SchedulableCapacityDisposition.AVAILABLE;
            case NO_ASSIGNMENT -> SchedulableCapacityDisposition.NO_ASSIGNMENT;
            case REPROBE_REQUIRED -> SchedulableCapacityDisposition.REPROBE_REQUIRED;
            case FAIL_CLOSED -> SchedulableCapacityDisposition.FAIL_CLOSED;
        };
    }

    private static void validateReservationScope(
            Collection<Reservation> reservations,
            PhysicalHostId physicalHostId) {
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        reservations.forEach(reservation -> {
            Objects.requireNonNull(reservation, "reservation");
            if (!physicalHostId.equals(reservation.physicalHostId())) {
                throw new IllegalArgumentException(
                        "reservation host scope does not match host resource snapshot");
            }
        });
    }

    private static boolean observationContradictsStaticCapacity(HostResourceSnapshot snapshot) {
        CapacitySnapshot capacity = snapshot.staticCapacity();
        ObservedUsage usage = snapshot.observedUsage();
        if (usage.memory().usedBytes() > capacity.memory().bytes()
                || usage.temporaryStorage().usedBytes() > capacity.temporaryStorage().bytes()) {
            return true;
        }
        return usage.deviceUsage().entrySet().stream().anyMatch(entry -> {
            DeviceResourceCapacity deviceCapacity = capacity.deviceResources().get(entry.getKey());
            return deviceCapacity == null || entry.getValue().vramUsedBytes() > deviceCapacity.vramBytes();
        });
    }

    private static long remaining(long capacity, long unavailable) {
        return unavailable >= capacity ? 0 : capacity - unavailable;
    }

    private static boolean hasCapacity(DeviceResourceCapacity capacity) {
        return capacity.vramBytes() != 0
                || capacity.computeUnits() != 0
                || capacity.encoderEngines() != 0
                || capacity.decoderEngines() != 0;
    }

    private static final class ResourceAccumulator {
        private long cpuMillicores;
        private long memoryBytes;
        private long temporaryStorageBytes;
        private final Map<DeviceId, DeviceTotals> devices = new LinkedHashMap<>();

        private void add(ReservedResources resources) {
            cpuMillicores = Math.addExact(cpuMillicores, resources.cpuMillicores());
            memoryBytes = Math.addExact(memoryBytes, resources.memoryBytes());
            temporaryStorageBytes = Math.addExact(
                    temporaryStorageBytes, resources.temporaryStorageBytes());
            resources.deviceResources().forEach((deviceId, reservation) -> devices.merge(
                    deviceId,
                    new DeviceTotals(
                            reservation.vramBytes(),
                            reservation.computeUnits(),
                            reservation.encoderEngines(),
                            reservation.decoderEngines()),
                    DeviceTotals::plus));
        }

        private void add(ResourceAccumulator other) {
            cpuMillicores = Math.addExact(cpuMillicores, other.cpuMillicores);
            memoryBytes = Math.addExact(memoryBytes, other.memoryBytes);
            temporaryStorageBytes = Math.addExact(
                    temporaryStorageBytes, other.temporaryStorageBytes);
            other.devices.forEach((deviceId, totals) ->
                    devices.merge(deviceId, totals, DeviceTotals::plus));
        }

        private boolean exceeds(CapacitySnapshot capacity) {
            if (cpuMillicores > capacity.cpu().millicores()
                    || memoryBytes > capacity.memory().bytes()
                    || temporaryStorageBytes > capacity.temporaryStorage().bytes()) {
                return true;
            }
            return devices.entrySet().stream().anyMatch(entry -> {
                DeviceResourceCapacity deviceCapacity = capacity.deviceResources().get(entry.getKey());
                DeviceTotals totals = entry.getValue();
                return deviceCapacity == null
                        || totals.vramBytes > deviceCapacity.vramBytes()
                        || totals.computeUnits > deviceCapacity.computeUnits()
                        || totals.encoderEngines > deviceCapacity.encoderEngines()
                        || totals.decoderEngines > deviceCapacity.decoderEngines();
            });
        }
    }

    private record DeviceTotals(
            long vramBytes,
            long computeUnits,
            long encoderEngines,
            long decoderEngines) {
        private static final DeviceTotals NONE = new DeviceTotals(0, 0, 0, 0);

        private DeviceTotals plus(DeviceTotals other) {
            return new DeviceTotals(
                    Math.addExact(vramBytes, other.vramBytes),
                    Math.addExact(computeUnits, other.computeUnits),
                    Math.addExact(encoderEngines, other.encoderEngines),
                    Math.addExact(decoderEngines, other.decoderEngines));
        }
    }
}
