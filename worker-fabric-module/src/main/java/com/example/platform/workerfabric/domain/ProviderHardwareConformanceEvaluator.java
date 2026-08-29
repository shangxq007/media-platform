package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Pure Stage-2-local evaluator; composition with runtime eligibility belongs to P20-I4. */
public final class ProviderHardwareConformanceEvaluator {

    private ProviderHardwareConformanceEvaluator() {}

    public static ProviderHardwareConformanceDecision evaluate(
            ProviderImplementationId expectedProviderImplementationId,
            WorkerRuntimeId expectedWorkerRuntimeId,
            PhysicalHostId expectedPhysicalHostId,
            Optional<DeviceId> expectedDeviceId,
            ProviderHardwareRequirement requirement,
            Optional<ProviderHardwareObservation> observation,
            Instant assessedAt) {
        Objects.requireNonNull(expectedProviderImplementationId, "expectedProviderImplementationId");
        Objects.requireNonNull(expectedWorkerRuntimeId, "expectedWorkerRuntimeId");
        Objects.requireNonNull(expectedPhysicalHostId, "expectedPhysicalHostId");
        Objects.requireNonNull(expectedDeviceId, "expectedDeviceId");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(assessedAt, "assessedAt");

        TreeSet<ProviderHardwareConformanceReason> reasons = new TreeSet<>();
        if (!requirement.providerImplementationId().equals(expectedProviderImplementationId)) {
            add(reasons, ProviderHardwareConformanceReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH);
        }
        if (observation.isEmpty()) {
            add(reasons, ProviderHardwareConformanceReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
            return result(reasons);
        }

        ProviderHardwareObservation exact = observation.orElseThrow();
        if (!exact.providerImplementationId().equals(expectedProviderImplementationId)) {
            add(reasons, ProviderHardwareConformanceReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH);
        }
        if (!exact.workerRuntimeId().equals(expectedWorkerRuntimeId)) {
            add(reasons, ProviderHardwareConformanceReasonCode.WORKER_RUNTIME_MISMATCH);
        }
        if (!exact.physicalHostId().equals(expectedPhysicalHostId)) {
            add(reasons, ProviderHardwareConformanceReasonCode.PHYSICAL_HOST_MISMATCH);
        }
        if (!exact.deviceId().equals(expectedDeviceId)) {
            add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_IDENTITY_MISMATCH);
        }
        if (!exact.isFreshAt(assessedAt)) {
            add(reasons, ProviderHardwareConformanceReasonCode.STALE_OBSERVATION);
        }

        switch (exact.evidence()) {
            case ProviderHardwareAvailableEvidence available -> {
                evaluateAvailable(requirement, available, reasons);
            }
            case ProviderHardwareRuntimeUnavailableEvidence ignored ->
                    add(reasons, ProviderHardwareConformanceReasonCode.RUNTIME_UNAVAILABLE);
            case ProviderHardwareProbeUnknownEvidence ignored ->
                    add(reasons, ProviderHardwareConformanceReasonCode.PROBE_UNKNOWN);
            case ProviderHardwareProbeFailedEvidence ignored ->
                    add(reasons, ProviderHardwareConformanceReasonCode.PROBE_FAILED);
        }
        return result(reasons);
    }

    private static void evaluateAvailable(
            ProviderHardwareRequirement requirement,
            ProviderHardwareAvailableEvidence available,
            TreeSet<ProviderHardwareConformanceReason> reasons) {
        if (requirement.cpuArchitecture() != available.cpuArchitecture()) {
            add(reasons, ProviderHardwareConformanceReasonCode.CPU_ARCHITECTURE_INCOMPATIBLE);
        }
        if (!available.providerBuildFeatures()
                .containsAll(requirement.requiredProviderBuildFeatures())) {
            add(reasons, ProviderHardwareConformanceReasonCode.PROVIDER_BUILD_FEATURE_MISSING);
        }
        if (!available.codecOrFilterFeatures()
                .containsAll(requirement.requiredCodecOrFilterFeatures())) {
            add(reasons, ProviderHardwareConformanceReasonCode.CODEC_OR_FILTER_FEATURE_MISSING);
        }
        if (!available.sandboxPermissions()
                .containsAll(requirement.requiredSandboxPermissions())) {
            add(reasons, ProviderHardwareConformanceReasonCode.SANDBOX_PERMISSION_UNAVAILABLE);
        }
        requirement.deviceRequirement().ifPresent(deviceRequirement ->
                evaluateDevice(deviceRequirement, available.deviceEvidence(), reasons));
    }

    private static void evaluateDevice(
            ProviderHardwareDeviceRequirement requirement,
            Optional<ProviderHardwareDeviceEvidence> evidence,
            TreeSet<ProviderHardwareConformanceReason> reasons) {
        if (evidence.isEmpty()) {
            add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
            return;
        }
        switch (evidence.orElseThrow()) {
            case ProviderHardwareNotExposedDevice ignored ->
                    add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_NOT_EXPOSED);
            case ProviderHardwareUnavailableDevice ignored ->
                    add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_UNAVAILABLE);
            case ProviderHardwareAvailableDevice available -> {
                boolean wrongClass = available.kind() != requirement.kind()
                        || requirement.vendorConstraint()
                                .map(vendor -> !vendor.equals(available.vendor()))
                                .orElse(false)
                        || requirement.modelConstraint()
                                .map(model -> !model.equals(available.model()))
                                .orElse(false);
                if (wrongClass) {
                    add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
                }
                DriverRuntimeRequirement driverRequirement = requirement.driverRuntimeRequirement();
                DriverRuntimeObservation driverObservation = available.driverRuntime();
                if (!driverRequirement.versionConstraint().matches(driverObservation.version())
                        || driverRequirement.abiConstraint().isPresent()
                                && !driverRequirement.abiConstraint().equals(driverObservation.abi())) {
                    add(reasons, ProviderHardwareConformanceReasonCode.DRIVER_RUNTIME_INCOMPATIBLE);
                }
                if (!available.availableFeatures().containsAll(requirement.requiredDeviceFeatures())) {
                    add(reasons, ProviderHardwareConformanceReasonCode.DEVICE_FEATURE_UNAVAILABLE);
                }
            }
        }
    }

    private static void add(
            TreeSet<ProviderHardwareConformanceReason> reasons,
            ProviderHardwareConformanceReasonCode code) {
        reasons.add(new ProviderHardwareConformanceReason(code));
    }

    private static ProviderHardwareConformanceDecision result(
            TreeSet<ProviderHardwareConformanceReason> reasons) {
        List<ProviderHardwareConformanceReason> ordered = List.copyOf(reasons);
        ProviderHardwareConformanceStatus status;
        if (ordered.isEmpty()) {
            status = ProviderHardwareConformanceStatus.CAN_RUN;
        } else if (ordered.stream().anyMatch(reason -> reason.code().unknownEvidence())) {
            status = ProviderHardwareConformanceStatus.UNKNOWN_FAIL_CLOSED;
        } else {
            status = ProviderHardwareConformanceStatus.CANNOT_RUN;
        }
        return new ProviderHardwareConformanceDecision(status, ordered);
    }
}
