package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderHardwareModelTest {

    private static final ProviderImplementationId PROVIDER =
            ProviderImplementationId.of("native.renderer.v1");
    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("runtime-a");
    private static final PhysicalHostId HOST = PhysicalHostId.of("host-a");
    private static final DeviceId DEVICE = DeviceId.of("gpu-0");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-29T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T10:05:00Z");

    @Test
    void feature_and_permission_sets_are_canonical_immutable_values() {
        ProviderHardwareRequirement requirement = requirement(
                List.of("module.video", "module.audio"),
                List.of("filter.scale", "codec.h264"),
                List.of("network.access", "device.access"));
        ProviderHardwareAvailableEvidence observation = availableEvidence(
                List.of("module.video", "module.audio"),
                List.of("filter.scale", "codec.h264"),
                List.of("network.access", "device.access"),
                List.of("tensor.compute", "memory.unified"));

        assertThat(requirement.requiredProviderBuildFeatures())
                .containsExactly("module.audio", "module.video");
        assertThat(requirement.requiredCodecOrFilterFeatures())
                .containsExactly("codec.h264", "filter.scale");
        assertThat(requirement.requiredSandboxPermissions())
                .containsExactly("device.access", "network.access");
        assertThat(observation.deviceEvidence().orElseThrow())
                .isInstanceOfSatisfying(ProviderHardwareAvailableDevice.class, device ->
                        assertThat(device.availableFeatures())
                                .containsExactly("memory.unified", "tensor.compute"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> requirement.requiredProviderBuildFeatures().add("module.other"));
    }

    @Test
    void duplicate_noncanonical_incomplete_and_invalid_window_construction_is_rejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> requirement(
                List.of("module.gpu", "module.gpu"), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> requirement(
                List.of(), List.of("Codec.H264"), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> availableEvidence(
                List.of(), List.of(), List.of("device.access", "device.access"), List.of()));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new DriverRuntimeObservation(null, Optional.empty()));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->
                new ProviderHardwareAvailableEvidence(
                        CpuArchitecture.X86_64, List.of(), List.of(), List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderHardwareObservation(
                PROVIDER,
                RUNTIME,
                HOST,
                Optional.empty(),
                OBSERVED_AT,
                EXPIRES_AT,
                availableEvidence(List.of(), List.of(), List.of(), List.of())));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderHardwareObservation(
                PROVIDER,
                RUNTIME,
                HOST,
                Optional.of(DEVICE),
                EXPIRES_AT,
                EXPIRES_AT,
                new ProviderHardwareProbeUnknownEvidence()));
    }

    @Test
    void decisions_reject_duplicate_or_noncanonical_reason_order() {
        ProviderHardwareConformanceReason cpu = reason(
                ProviderHardwareConformanceReasonCode.CPU_ARCHITECTURE_INCOMPATIBLE);
        ProviderHardwareConformanceReason sandbox = reason(
                ProviderHardwareConformanceReasonCode.SANDBOX_PERMISSION_UNAVAILABLE);

        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderHardwareConformanceDecision(
                ProviderHardwareConformanceStatus.CANNOT_RUN, List.of(cpu, cpu)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProviderHardwareConformanceDecision(
                ProviderHardwareConformanceStatus.CANNOT_RUN, List.of(sandbox, cpu)));
    }

    @Test
    void feature_permutations_produce_the_same_stably_ordered_reasons() {
        ProviderHardwareRequirement first = requirement(
                List.of("module.video", "module.audio"),
                List.of("filter.scale", "codec.h264"),
                List.of("network.access", "device.access"));
        ProviderHardwareRequirement second = requirement(
                List.of("module.audio", "module.video"),
                List.of("codec.h264", "filter.scale"),
                List.of("device.access", "network.access"));
        ProviderHardwareAvailableEvidence evidence = availableEvidence(
                List.of(), List.of(), List.of(), List.of());

        ProviderHardwareConformanceDecision firstDecision = evaluate(first, evidence);
        ProviderHardwareConformanceDecision secondDecision = evaluate(second, evidence);

        assertThat(firstDecision).isEqualTo(secondDecision);
        assertThat(firstDecision.reasons())
                .extracting(ProviderHardwareConformanceReason::code)
                .containsExactly(
                        ProviderHardwareConformanceReasonCode.PROVIDER_BUILD_FEATURE_MISSING,
                        ProviderHardwareConformanceReasonCode.CODEC_OR_FILTER_FEATURE_MISSING,
                        ProviderHardwareConformanceReasonCode.DEVICE_FEATURE_UNAVAILABLE,
                        ProviderHardwareConformanceReasonCode.SANDBOX_PERMISSION_UNAVAILABLE);
    }

    @Test
    void technical_model_exposes_no_policy_cost_trust_or_optimization_state() {
        List<Class<?>> modelTypes = List.of(
                ProviderHardwareRequirement.class,
                ProviderHardwareDeviceRequirement.class,
                ProviderHardwareObservation.class,
                ProviderHardwareAvailableEvidence.class,
                ProviderHardwareAvailableDevice.class,
                DriverRuntimeRequirement.class,
                DriverRuntimeObservation.class,
                ProviderHardwareConformanceDecision.class,
                ProviderHardwareConformanceReason.class);
        List<String> forbidden = List.of(
                "quota", "cost", "price", "billing", "entitlement", "trust", "score", "rank", "optimiz");

        for (Class<?> type : modelTypes) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertThat(component.getName().toLowerCase(Locale.ROOT))
                        .as("record component on %s", type.getSimpleName())
                        .doesNotContain(forbidden.toArray(String[]::new));
                assertThat(component.getType().getName().toLowerCase(Locale.ROOT))
                        .as("record component type on %s", type.getSimpleName())
                        .doesNotContain(forbidden.toArray(String[]::new));
            }
            assertThat(Arrays.stream(type.getDeclaredFields())
                            .map(Field::getType)
                            .map(Class::getName)
                            .map(name -> name.toLowerCase(Locale.ROOT)))
                    .as("field imports on %s", type.getSimpleName())
                    .allSatisfy(name -> assertThat(name)
                            .doesNotContain(forbidden.toArray(String[]::new)));
        }
    }

    private static ProviderHardwareRequirement requirement(
            List<String> buildFeatures,
            List<String> codecOrFilterFeatures,
            List<String> sandboxPermissions) {
        return new ProviderHardwareRequirement(
                PROVIDER,
                CpuArchitecture.X86_64,
                Optional.of(new ProviderHardwareDeviceRequirement(
                        DeviceKind.GPU,
                        Optional.empty(),
                        Optional.empty(),
                        new DriverRuntimeRequirement(
                                RuntimeDependencyVersionConstraint.exact(
                                        RuntimeDependencyVersion.of("12.4")),
                                Optional.empty()),
                        List.of("tensor.compute"))),
                buildFeatures,
                codecOrFilterFeatures,
                sandboxPermissions);
    }

    private static ProviderHardwareAvailableEvidence availableEvidence(
            List<String> buildFeatures,
            List<String> codecOrFilterFeatures,
            List<String> sandboxPermissions,
            List<String> deviceFeatures) {
        return new ProviderHardwareAvailableEvidence(
                CpuArchitecture.X86_64,
                buildFeatures,
                codecOrFilterFeatures,
                sandboxPermissions,
                Optional.of(new ProviderHardwareAvailableDevice(
                        DeviceKind.GPU,
                        DeviceVendor.of("vendor-a"),
                        DeviceModel.of("model-a"),
                        new DriverRuntimeObservation(
                                RuntimeDependencyVersion.of("12.4"), Optional.empty()),
                        deviceFeatures)));
    }

    private static ProviderHardwareConformanceDecision evaluate(
            ProviderHardwareRequirement requirement,
            ProviderHardwareAvailableEvidence evidence) {
        return ProviderHardwareConformanceEvaluator.evaluate(
                PROVIDER,
                RUNTIME,
                HOST,
                Optional.of(DEVICE),
                requirement,
                Optional.of(new ProviderHardwareObservation(
                        PROVIDER, RUNTIME, HOST, Optional.of(DEVICE), OBSERVED_AT, EXPIRES_AT, evidence)),
                OBSERVED_AT);
    }

    private static ProviderHardwareConformanceReason reason(
            ProviderHardwareConformanceReasonCode code) {
        return new ProviderHardwareConformanceReason(code);
    }
}
