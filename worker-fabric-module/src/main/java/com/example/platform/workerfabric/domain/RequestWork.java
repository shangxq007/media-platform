package com.example.platform.workerfabric.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker-supplied Native Pull readiness and runtime evidence.
 *
 * <p>The request deliberately has no task, provider, backend, queue, priority, fairness, deadline,
 * or placement selection field. Those remain central authorities.
 */
public record RequestWork(
        RequestWorkId requestWorkId,
        WorkerRuntimeId workerRuntimeId,
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId,
        PhysicalHostId physicalHostId,
        PhysicalHostIncarnationId physicalHostIncarnationId,
        HostResourceSnapshot hostResourceSnapshot,
        WorkerRuntimeAvailability workerRuntimeAvailability,
        Map<DeviceId, DeviceAvailability> deviceAvailability,
        RuntimeEnvironmentAvailability runtimeEnvironmentAvailability,
        SandboxRuntimeAvailability sandboxRuntimeAvailability,
        Optional<ProviderHardwareObservation> providerHardwareObservation,
        Optional<RuntimeDependencyObservation> runtimeDependencyObservation,
        Optional<WorkerRuntimeSupportAdvertisement> runtimeSupportAdvertisement,
        Optional<SchedulableCapacity> workerDerivedSchedulableCapacity) {

    public RequestWork {
        Objects.requireNonNull(requestWorkId, "requestWorkId");
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(workerRuntimeIncarnationId, "workerRuntimeIncarnationId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(hostResourceSnapshot, "hostResourceSnapshot");
        Objects.requireNonNull(workerRuntimeAvailability, "workerRuntimeAvailability");
        deviceAvailability = Map.copyOf(Objects.requireNonNull(
                deviceAvailability, "deviceAvailability"));
        Objects.requireNonNull(runtimeEnvironmentAvailability,
                "runtimeEnvironmentAvailability");
        Objects.requireNonNull(sandboxRuntimeAvailability, "sandboxRuntimeAvailability");
        providerHardwareObservation = Objects.requireNonNull(
                providerHardwareObservation, "providerHardwareObservation");
        runtimeDependencyObservation = Objects.requireNonNull(
                runtimeDependencyObservation, "runtimeDependencyObservation");
        runtimeSupportAdvertisement = Objects.requireNonNull(
                runtimeSupportAdvertisement, "runtimeSupportAdvertisement");
        workerDerivedSchedulableCapacity = Objects.requireNonNull(
                workerDerivedSchedulableCapacity, "workerDerivedSchedulableCapacity");

        if (!workerRuntimeAvailability.matchesCurrentIncarnation(
                workerRuntimeId, workerRuntimeIncarnationId)) {
            throw new IllegalArgumentException(
                    "RequestWork availability must bind the exact worker-runtime incarnation");
        }
        if (!physicalHostId.equals(hostResourceSnapshot.physicalHostId())
                || !physicalHostIncarnationId.equals(
                        hostResourceSnapshot.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "RequestWork snapshot must bind the exact physical-host incarnation");
        }
        deviceAvailability.forEach((deviceId, availability) -> {
            Objects.requireNonNull(deviceId, "deviceAvailability key");
            Objects.requireNonNull(availability, "deviceAvailability value");
            if (!deviceId.equals(availability.deviceId())) {
                throw new IllegalArgumentException(
                        "RequestWork device availability key must match its DeviceId");
            }
            if (!hostResourceSnapshot.staticCapacity().deviceResources().containsKey(deviceId)) {
                throw new IllegalArgumentException(
                        "RequestWork device evidence must reference the exact host snapshot");
            }
        });
        hostResourceSnapshot.reportingRuntime().ifPresent(reporter -> {
            if (!workerRuntimeId.equals(reporter.workerRuntimeId())
                    || !workerRuntimeIncarnationId.equals(
                            reporter.workerRuntimeIncarnationId())) {
                throw new IllegalArgumentException(
                        "RequestWork reporting runtime must bind the exact runtime incarnation");
            }
        });
        workerDerivedSchedulableCapacity.ifPresent(capacity -> {
            if (!physicalHostId.equals(capacity.physicalHostId())
                    || !physicalHostIncarnationId.equals(
                            capacity.physicalHostIncarnationId())) {
                throw new IllegalArgumentException(
                        "worker-derived capacity must bind the exact host incarnation");
            }
        });
        runtimeSupportAdvertisement.ifPresent(advertisement -> {
            if (!workerRuntimeId.equals(advertisement.runtimeId())) {
                throw new IllegalArgumentException(
                        "runtime support advertisement must bind the requesting runtime");
            }
        });
        providerHardwareObservation.ifPresent(observation -> {
            if (!workerRuntimeId.equals(observation.workerRuntimeId())
                    || !physicalHostId.equals(observation.physicalHostId())) {
                throw new IllegalArgumentException(
                        "provider hardware observation must bind the requesting runtime and host");
            }
            observation.deviceId().ifPresent(deviceId -> {
                if (!hostResourceSnapshot.staticCapacity().deviceResources().containsKey(deviceId)) {
                    throw new IllegalArgumentException(
                            "provider hardware observation device must belong to the exact host snapshot");
                }
            });
        });
        runtimeDependencyObservation.ifPresent(observation -> {
            if (!workerRuntimeId.equals(observation.workerRuntimeId())) {
                throw new IllegalArgumentException(
                        "runtime dependency observation must bind the requesting runtime");
            }
            observation.deviceId().ifPresent(deviceId -> {
                if (!hostResourceSnapshot.staticCapacity().deviceResources().containsKey(deviceId)) {
                    throw new IllegalArgumentException(
                            "runtime dependency observation device must belong to the exact host snapshot");
                }
            });
        });
        if (providerHardwareObservation.isPresent()
                && runtimeDependencyObservation.isPresent()
                && !providerHardwareObservation.orElseThrow().providerImplementationId().equals(
                        runtimeDependencyObservation.orElseThrow().providerImplementationId())) {
            throw new IllegalArgumentException(
                    "RequestWork probe observations must bind one ProviderImplementationId");
        }
    }
}
