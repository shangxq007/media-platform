package com.example.platform.workerfabric.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Capacity available for placement after the frozen reservation-first rule.
 *
 * <p>STATIC_CAPACITY - ACTIVE_RESERVATIONS - RECOVERY_HOLD_RESERVATIONS
 * - RESIDENT_RESERVATIONS - SAFETY_HEADROOM. Observed usage is deliberately not an input.
 */
public record SchedulableCapacity(
        boolean available,
        CpuCapacity cpu,
        MemoryCapacity memory,
        TemporaryStorageCapacity temporaryStorage,
        Map<DeviceId, DeviceResourceCapacity> deviceResources) {

    public SchedulableCapacity {
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
        if (!available && (cpu.millicores() != 0
                || memory.bytes() != 0
                || temporaryStorage.bytes() != 0
                || deviceResources.values().stream().anyMatch(SchedulableCapacity::hasCapacity))) {
            throw new IllegalArgumentException("unavailable schedulable capacity must be zero");
        }
    }

    public static SchedulableCapacity forHost(
            CapacitySnapshot staticCapacity,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostAvailability hostAvailability) {
        Objects.requireNonNull(hostAvailability, "hostAvailability");
        return calculate(
                staticCapacity,
                reservations,
                safetyHeadroom,
                hostAvailability.physicalHostId(),
                hostAvailability.isReachable());
    }

    public static SchedulableCapacity forRuntime(
            CapacitySnapshot staticCapacity,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding) {
        validateRuntimeBinding(hostAvailability, runtimeAvailability, binding);
        return calculate(
                staticCapacity,
                reservations,
                safetyHeadroom,
                hostAvailability.physicalHostId(),
                hostAvailability.isReachable() && runtimeAvailability.isReachable());
    }

    public static SchedulableCapacity forRuntime(
            CapacitySnapshot staticCapacity,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding,
            WorkerRuntimeDescriptor runtimeDescriptor) {
        validateRuntimeBinding(hostAvailability, runtimeAvailability, binding);
        validateRuntimeDescriptor(binding, runtimeDescriptor);
        return calculate(
                staticCapacity,
                reservations,
                safetyHeadroom,
                hostAvailability.physicalHostId(),
                hostAvailability.isReachable() && runtimeAvailability.isReachable());
    }

    private static void validateRuntimeBinding(
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding) {
        Objects.requireNonNull(hostAvailability, "hostAvailability");
        Objects.requireNonNull(runtimeAvailability, "runtimeAvailability");
        Objects.requireNonNull(binding, "binding");

        if (!binding.workerRuntimeId().equals(runtimeAvailability.workerRuntimeId())) {
            throw new IllegalArgumentException(
                    "local runtime binding WorkerRuntimeId does not match runtime availability");
        }
        if (!binding.workerRuntimeIncarnationId().equals(runtimeAvailability.incarnationId())) {
            throw new IllegalArgumentException(
                    "local runtime binding WorkerRuntimeIncarnationId does not match runtime availability");
        }
        if (!binding.physicalHostId().equals(hostAvailability.physicalHostId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostId does not match host availability");
        }
        if (!binding.physicalHostIncarnationId().equals(hostAvailability.incarnationId())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostIncarnationId does not match host availability");
        }
    }

    private static void validateRuntimeDescriptor(
            LocalWorkerRuntimeIncarnationBinding binding,
            WorkerRuntimeDescriptor runtimeDescriptor) {
        Objects.requireNonNull(runtimeDescriptor, "runtimeDescriptor");
        if (!binding.workerRuntimeId().equals(runtimeDescriptor.id())) {
            throw new IllegalArgumentException(
                    "local runtime binding WorkerRuntimeId does not match runtime descriptor");
        }
        if (runtimeDescriptor.physicalHostId().isEmpty()
                || !binding.physicalHostId().equals(runtimeDescriptor.physicalHostId().orElseThrow())) {
            throw new IllegalArgumentException(
                    "local runtime binding PhysicalHostId does not match runtime descriptor");
        }
    }

    private static SchedulableCapacity calculate(
            CapacitySnapshot staticCapacity,
            Collection<Reservation> reservations,
            SafetyHeadroom safetyHeadroom,
            PhysicalHostId physicalHostId,
            boolean targetReachable) {
        Objects.requireNonNull(staticCapacity, "staticCapacity");
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(safetyHeadroom, "safetyHeadroom");
        Objects.requireNonNull(physicalHostId, "physicalHostId");

        if (!targetReachable) {
            return unavailable(staticCapacity);
        }

        ResourceAccumulator active = new ResourceAccumulator();
        ResourceAccumulator recoveryHold = new ResourceAccumulator();
        ResourceAccumulator resident = new ResourceAccumulator();

        for (Reservation reservation : reservations) {
            Objects.requireNonNull(reservation, "reservation");
            if (!physicalHostId.equals(reservation.physicalHostId())
                    || reservation.state() == ReservationState.RELEASED) {
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

        return subtract(staticCapacity, unavailable);
    }

    private static SchedulableCapacity subtract(
            CapacitySnapshot staticCapacity,
            ResourceAccumulator unavailable) {
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
                true,
                CpuCapacity.ofMillicores(remaining(
                        staticCapacity.cpu().millicores(), unavailable.cpuMillicores)),
                MemoryCapacity.ofBytes(remaining(
                        staticCapacity.memory().bytes(), unavailable.memoryBytes)),
                TemporaryStorageCapacity.ofBytes(remaining(
                        staticCapacity.temporaryStorage().bytes(), unavailable.temporaryStorageBytes)),
                remainingDevices);
    }

    private static SchedulableCapacity unavailable(CapacitySnapshot staticCapacity) {
        Map<DeviceId, DeviceResourceCapacity> unavailableDevices = new LinkedHashMap<>();
        staticCapacity.deviceResources().keySet()
                .forEach(deviceId -> unavailableDevices.put(deviceId, DeviceResourceCapacity.none(deviceId)));
        return new SchedulableCapacity(
                false,
                CpuCapacity.ofMillicores(0),
                MemoryCapacity.ofBytes(0),
                TemporaryStorageCapacity.ofBytes(0),
                unavailableDevices);
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
