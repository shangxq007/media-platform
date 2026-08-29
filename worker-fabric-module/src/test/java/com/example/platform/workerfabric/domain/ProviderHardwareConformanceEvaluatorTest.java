package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderHardwareConformanceEvaluatorTest {

    private static final ProviderImplementationId PROVIDER =
            ProviderImplementationId.of("native.renderer.v1");
    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("runtime-a");
    private static final PhysicalHostId HOST = PhysicalHostId.of("host-a");
    private static final DeviceId DEVICE = DeviceId.of("gpu-0");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-29T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T10:05:00Z");

    @Test
    void exact_typed_hardware_and_runtime_evidence_can_run() {
        ProviderHardwareConformanceDecision decision = evaluate(
                PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                Optional.of(observation(availableEvidence())), OBSERVED_AT);

        assertThat(decision.status()).isEqualTo(ProviderHardwareConformanceStatus.CAN_RUN);
        assertThat(decision.canRun()).isTrue();
        assertThat(decision.reasons()).isEmpty();
    }

    @Test
    void provider_runtime_host_and_device_bindings_are_exact() {
        ProviderImplementationId otherProvider = ProviderImplementationId.of("native.renderer.v2");
        assertOnlyReason(
                evaluate(
                        otherProvider,
                        RUNTIME,
                        HOST,
                        Optional.of(DEVICE),
                        requirement(otherProvider),
                        Optional.of(observation(availableEvidence())),
                        OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH);
        assertOnlyReason(
                evaluate(
                        PROVIDER,
                        WorkerRuntimeId.of("runtime-b"),
                        HOST,
                        Optional.of(DEVICE),
                        requirement(PROVIDER),
                        Optional.of(observation(availableEvidence())),
                        OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.WORKER_RUNTIME_MISMATCH);
        assertOnlyReason(
                evaluate(
                        PROVIDER,
                        RUNTIME,
                        PhysicalHostId.of("host-b"),
                        Optional.of(DEVICE),
                        requirement(PROVIDER),
                        Optional.of(observation(availableEvidence())),
                        OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.PHYSICAL_HOST_MISMATCH);
        assertOnlyReason(
                evaluate(
                        PROVIDER,
                        RUNTIME,
                        HOST,
                        Optional.of(DeviceId.of("gpu-1")),
                        requirement(PROVIDER),
                        Optional.of(observation(availableEvidence())),
                        OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.DEVICE_IDENTITY_MISMATCH);
    }

    @Test
    void device_binding_is_preserved_when_the_probe_is_unknown() {
        ProviderHardwareObservation unknownOnOtherDevice = new ProviderHardwareObservation(
                PROVIDER,
                RUNTIME,
                HOST,
                Optional.of(DeviceId.of("gpu-1")),
                OBSERVED_AT,
                EXPIRES_AT,
                new ProviderHardwareProbeUnknownEvidence());

        ProviderHardwareConformanceDecision decision = evaluate(
                PROVIDER,
                RUNTIME,
                HOST,
                Optional.of(DEVICE),
                requirement(PROVIDER),
                Optional.of(unknownOnOtherDevice),
                OBSERVED_AT);

        assertThat(decision.status()).isEqualTo(ProviderHardwareConformanceStatus.UNKNOWN_FAIL_CLOSED);
        assertThat(decision.reasons())
                .extracting(ProviderHardwareConformanceReason::code)
                .containsExactly(
                        ProviderHardwareConformanceReasonCode.DEVICE_IDENTITY_MISMATCH,
                        ProviderHardwareConformanceReasonCode.PROBE_UNKNOWN);
    }

    @Test
    void missing_unknown_failed_and_runtime_unavailable_evidence_fail_closed() {
        assertOnlyUnknownReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.empty(), OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
        assertOnlyUnknownReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(observation(new ProviderHardwareProbeUnknownEvidence())), OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.PROBE_UNKNOWN);
        assertOnlyUnknownReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(observation(new ProviderHardwareProbeFailedEvidence())), OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.PROBE_FAILED);
        assertOnlyReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(observation(new ProviderHardwareRuntimeUnavailableEvidence())), OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.RUNTIME_UNAVAILABLE);
    }

    @Test
    void freshness_is_inclusive_at_observation_and_exclusive_at_expiry() {
        ProviderHardwareObservation exact = observation(availableEvidence());

        assertThat(evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(exact), OBSERVED_AT)
                .status()).isEqualTo(ProviderHardwareConformanceStatus.CAN_RUN);
        assertOnlyUnknownReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(exact), OBSERVED_AT.minusNanos(1)),
                ProviderHardwareConformanceReasonCode.STALE_OBSERVATION);
        assertOnlyUnknownReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(exact), EXPIRES_AT),
                ProviderHardwareConformanceReasonCode.STALE_OBSERVATION);
    }

    @Test
    void cpu_architecture_mismatch_has_its_own_reason() {
        ProviderHardwareAvailableEvidence evidence = new ProviderHardwareAvailableEvidence(
                CpuArchitecture.AARCH64,
                List.of("module.gpu"),
                List.of("codec.h264", "filter.scale"),
                List.of("device.access"),
                Optional.of(availableDevice()));

        assertOnlyReason(evaluate(requirement(PROVIDER), evidence),
                ProviderHardwareConformanceReasonCode.CPU_ARCHITECTURE_INCOMPATIBLE);
    }

    @Test
    void device_class_and_material_vendor_or_model_constraints_are_exact() {
        assertOnlyReason(
                evaluate(
                        requirementWithDevice(deviceRequirement(
                                DeviceKind.MEDIA_ACCELERATOR,
                                DeviceVendor.of("vendor-a"),
                                DeviceModel.of("model-a"))),
                        availableEvidence()),
                ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
        assertOnlyReason(
                evaluate(
                        requirementWithDevice(deviceRequirement(
                                DeviceKind.GPU,
                                DeviceVendor.of("vendor-b"),
                                DeviceModel.of("model-a"))),
                        availableEvidence()),
                ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
        assertOnlyReason(
                evaluate(
                        requirementWithDevice(deviceRequirement(
                                DeviceKind.GPU,
                                DeviceVendor.of("vendor-a"),
                                DeviceModel.of("model-b"))),
                        availableEvidence()),
                ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
    }

    @Test
    void driver_runtime_version_and_optional_abi_constraints_are_exact() {
        assertOnlyReason(
                evaluate(requirement(PROVIDER), availableEvidence(availableDevice(
                        RuntimeDependencyVersion.of("11.9"), RuntimeDependencyAbi.of("driver.12")))),
                ProviderHardwareConformanceReasonCode.DRIVER_RUNTIME_INCOMPATIBLE);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), availableEvidence(availableDevice(
                        RuntimeDependencyVersion.of("12.4"), RuntimeDependencyAbi.of("driver.11")))),
                ProviderHardwareConformanceReasonCode.DRIVER_RUNTIME_INCOMPATIBLE);
    }

    @Test
    void each_feature_and_sandbox_dimension_has_its_own_reason() {
        assertOnlyReason(
                evaluate(requirement(PROVIDER), new ProviderHardwareAvailableEvidence(
                        CpuArchitecture.X86_64,
                        List.of(),
                        List.of("codec.h264", "filter.scale"),
                        List.of("device.access"),
                        Optional.of(availableDevice()))),
                ProviderHardwareConformanceReasonCode.PROVIDER_BUILD_FEATURE_MISSING);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), new ProviderHardwareAvailableEvidence(
                        CpuArchitecture.X86_64,
                        List.of("module.gpu"),
                        List.of("codec.h264"),
                        List.of("device.access"),
                        Optional.of(availableDevice()))),
                ProviderHardwareConformanceReasonCode.CODEC_OR_FILTER_FEATURE_MISSING);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), availableEvidence(new ProviderHardwareAvailableDevice(
                        DeviceKind.GPU,
                        DeviceVendor.of("vendor-a"),
                        DeviceModel.of("model-a"),
                        driverObservation(),
                        List.of()))),
                ProviderHardwareConformanceReasonCode.DEVICE_FEATURE_UNAVAILABLE);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), new ProviderHardwareAvailableEvidence(
                        CpuArchitecture.X86_64,
                        List.of("module.gpu"),
                        List.of("codec.h264", "filter.scale"),
                        List.of(),
                        Optional.of(availableDevice()))),
                ProviderHardwareConformanceReasonCode.SANDBOX_PERMISSION_UNAVAILABLE);
    }

    @Test
    void required_device_absent_not_exposed_and_unavailable_are_distinct() {
        assertOnlyReason(
                evaluate(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement(PROVIDER),
                        Optional.of(observation(availableEvidence(Optional.empty()))), OBSERVED_AT),
                ProviderHardwareConformanceReasonCode.DEVICE_CLASS_UNAVAILABLE);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), availableEvidence(
                        new ProviderHardwareNotExposedDevice())),
                ProviderHardwareConformanceReasonCode.DEVICE_NOT_EXPOSED);
        assertOnlyReason(
                evaluate(requirement(PROVIDER), availableEvidence(
                        new ProviderHardwareUnavailableDevice())),
                ProviderHardwareConformanceReasonCode.DEVICE_UNAVAILABLE);
    }

    @Test
    void no_device_requirement_accepts_complete_device_free_evidence() {
        ProviderHardwareRequirement noDevice = new ProviderHardwareRequirement(
                PROVIDER,
                CpuArchitecture.X86_64,
                Optional.empty(),
                List.of("module.gpu"),
                List.of("codec.h264", "filter.scale"),
                List.of("device.access"));

        ProviderHardwareConformanceDecision decision = evaluate(
                PROVIDER, RUNTIME, HOST, Optional.empty(), noDevice,
                Optional.of(observation(Optional.empty(), availableEvidence(Optional.empty()))), OBSERVED_AT);

        assertThat(decision.status()).isEqualTo(ProviderHardwareConformanceStatus.CAN_RUN);
        assertThat(decision.reasons()).isEmpty();
    }

    private static ProviderHardwareRequirement requirement(ProviderImplementationId provider) {
        return new ProviderHardwareRequirement(
                provider,
                CpuArchitecture.X86_64,
                Optional.of(new ProviderHardwareDeviceRequirement(
                        DeviceKind.GPU,
                        Optional.of(DeviceVendor.of("vendor-a")),
                        Optional.of(DeviceModel.of("model-a")),
                        new DriverRuntimeRequirement(
                                RuntimeDependencyVersionConstraint.range(
                                        RuntimeDependencyVersion.of("12"),
                                        RuntimeDependencyVersion.of("13")),
                                Optional.of(RuntimeDependencyAbi.of("driver.12"))),
                        List.of("tensor.compute"))),
                List.of("module.gpu"),
                List.of("codec.h264", "filter.scale"),
                List.of("device.access"));
    }

    private static ProviderHardwareRequirement requirementWithDevice(
            ProviderHardwareDeviceRequirement deviceRequirement) {
        return new ProviderHardwareRequirement(
                PROVIDER,
                CpuArchitecture.X86_64,
                Optional.of(deviceRequirement),
                List.of("module.gpu"),
                List.of("codec.h264", "filter.scale"),
                List.of("device.access"));
    }

    private static ProviderHardwareDeviceRequirement deviceRequirement(
            DeviceKind kind, DeviceVendor vendor, DeviceModel model) {
        return new ProviderHardwareDeviceRequirement(
                kind,
                Optional.of(vendor),
                Optional.of(model),
                driverRequirement(),
                List.of("tensor.compute"));
    }

    private static DriverRuntimeRequirement driverRequirement() {
        return new DriverRuntimeRequirement(
                RuntimeDependencyVersionConstraint.range(
                        RuntimeDependencyVersion.of("12"), RuntimeDependencyVersion.of("13")),
                Optional.of(RuntimeDependencyAbi.of("driver.12")));
    }

    private static ProviderHardwareAvailableEvidence availableEvidence() {
        return availableEvidence(availableDevice());
    }

    private static ProviderHardwareAvailableEvidence availableEvidence(
            ProviderHardwareDeviceEvidence deviceEvidence) {
        return availableEvidence(Optional.of(deviceEvidence));
    }

    private static ProviderHardwareAvailableEvidence availableEvidence(
            Optional<ProviderHardwareDeviceEvidence> deviceEvidence) {
        return new ProviderHardwareAvailableEvidence(
                CpuArchitecture.X86_64,
                List.of("module.gpu"),
                List.of("filter.scale", "codec.h264"),
                List.of("device.access"),
                deviceEvidence);
    }

    private static ProviderHardwareAvailableDevice availableDevice() {
        return availableDevice(
                RuntimeDependencyVersion.of("12.4"), RuntimeDependencyAbi.of("driver.12"));
    }

    private static ProviderHardwareAvailableDevice availableDevice(
            RuntimeDependencyVersion version, RuntimeDependencyAbi abi) {
        return new ProviderHardwareAvailableDevice(
                DeviceKind.GPU,
                DeviceVendor.of("vendor-a"),
                DeviceModel.of("model-a"),
                new DriverRuntimeObservation(version, Optional.of(abi)),
                List.of("tensor.compute"));
    }

    private static DriverRuntimeObservation driverObservation() {
        return new DriverRuntimeObservation(
                RuntimeDependencyVersion.of("12.4"),
                Optional.of(RuntimeDependencyAbi.of("driver.12")));
    }

    private static ProviderHardwareObservation observation(ProviderHardwareProbeEvidence evidence) {
        return observation(Optional.of(DEVICE), evidence);
    }

    private static ProviderHardwareObservation observation(
            Optional<DeviceId> deviceId, ProviderHardwareProbeEvidence evidence) {
        return new ProviderHardwareObservation(
                PROVIDER, RUNTIME, HOST, deviceId, OBSERVED_AT, EXPIRES_AT, evidence);
    }

    private static ProviderHardwareConformanceDecision evaluate(
            ProviderImplementationId provider,
            WorkerRuntimeId runtime,
            PhysicalHostId host,
            Optional<DeviceId> device,
            ProviderHardwareRequirement requirement,
            Optional<ProviderHardwareObservation> observation,
            Instant assessedAt) {
        return ProviderHardwareConformanceEvaluator.evaluate(
                provider, runtime, host, device, requirement, observation, assessedAt);
    }

    private static ProviderHardwareConformanceDecision evaluate(
            ProviderHardwareRequirement requirement,
            ProviderHardwareAvailableEvidence evidence) {
        return evaluate(
                PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), requirement,
                Optional.of(observation(evidence)), OBSERVED_AT);
    }

    private static void assertOnlyReason(
            ProviderHardwareConformanceDecision decision,
            ProviderHardwareConformanceReasonCode reason) {
        assertThat(decision.status()).isEqualTo(ProviderHardwareConformanceStatus.CANNOT_RUN);
        assertThat(decision.canRun()).isFalse();
        assertThat(decision.reasons())
                .extracting(ProviderHardwareConformanceReason::code)
                .containsExactly(reason);
    }

    private static void assertOnlyUnknownReason(
            ProviderHardwareConformanceDecision decision,
            ProviderHardwareConformanceReasonCode reason) {
        assertThat(decision.status()).isEqualTo(ProviderHardwareConformanceStatus.UNKNOWN_FAIL_CLOSED);
        assertThat(decision.canRun()).isFalse();
        assertThat(decision.reasons())
                .extracting(ProviderHardwareConformanceReason::code)
                .containsExactly(reason);
    }
}
