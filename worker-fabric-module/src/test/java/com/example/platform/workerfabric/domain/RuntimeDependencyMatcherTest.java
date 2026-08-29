package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeDependencyMatcherTest {

    private static final ProviderImplementationId PROVIDER =
            ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1");
    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("runtime-a");
    private static final DeviceId DEVICE = DeviceId.of("gpu-0");
    private static final RuntimeDependencyCoordinate FFMPEG =
            RuntimeDependencyCoordinate.of("ffmpeg.executable");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-29T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T10:05:00Z");

    @Test
    void exact_provider_runtime_device_and_dependency_evidence_can_match() {
        RuntimeDependencyMatchResult result = match(requirement(), observation(observedDependency()));

        assertThat(result.status()).isEqualTo(RuntimeDependencyMatchStatus.CAN_MATCH);
        assertThat(result.canMatch()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void provider_runtime_and_device_identity_mismatches_are_exact_and_fail_closed() {
        RuntimeDependencyObservation exact = observation(observedDependency());
        ProviderImplementationId otherProvider = ProviderImplementationId.of("ffmpeg.other");

        assertOnlyReason(
                RuntimeDependencyMatcher.match(
                        otherProvider,
                        RUNTIME,
                        Optional.of(DEVICE),
                        List.of(requirement(otherProvider)),
                        Optional.of(exact),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH);
        assertOnlyReason(
                RuntimeDependencyMatcher.match(
                        PROVIDER,
                        WorkerRuntimeId.of("runtime-b"),
                        Optional.of(DEVICE),
                        List.of(requirement()),
                        Optional.of(exact),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.WORKER_RUNTIME_MISMATCH);
        assertOnlyReason(
                RuntimeDependencyMatcher.match(
                        PROVIDER,
                        RUNTIME,
                        Optional.of(DeviceId.of("gpu-1")),
                        List.of(requirement()),
                        Optional.of(exact),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.DEVICE_BINDING_MISMATCH);
    }

    @Test
    void every_dependency_mismatch_reason_is_reported_separately() {
        assertOnlyReason(
                match(requirement(), observation()),
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_MISSING);
        assertOnlyReason(
                match(requirement(), observation(observedDependency("5.1", "libavcodec.60", List.of(
                        "codec.h264"), List.of("enable.gpl")))),
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE);
        assertOnlyReason(
                match(requirement(), observation(observedDependency("6.1.2", "libavcodec.59", List.of(
                        "codec.h264"), List.of("enable.gpl")))),
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE);
        assertOnlyReason(
                match(requirement(), observation(observedDependency("6.1.2", "libavcodec.60", List.of(), List.of(
                        "enable.gpl")))),
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_FEATURE_MISSING);
        assertOnlyReason(
                match(requirement(), observation(observedDependency("6.1.2", "libavcodec.60", List.of(
                        "codec.h264"), List.of()))),
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING);
    }

    @Test
    void stale_boundary_and_incomplete_observation_are_unknown_fail_closed() {
        RuntimeDependencyObservation exact = observation(observedDependency());

        assertThat(matchAt(exact, OBSERVED_AT).status()).isEqualTo(RuntimeDependencyMatchStatus.CAN_MATCH);
        assertOnlyUnknownReason(
                matchAt(exact, OBSERVED_AT.minusNanos(1)),
                RuntimeDependencyMatchReasonCode.STALE_OBSERVATION);
        assertOnlyUnknownReason(
                matchAt(exact, EXPIRES_AT),
                RuntimeDependencyMatchReasonCode.STALE_OBSERVATION);
        assertOnlyUnknownReason(
                RuntimeDependencyMatcher.match(
                        PROVIDER,
                        RUNTIME,
                        Optional.of(DEVICE),
                        List.of(requirement()),
                        Optional.empty(),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
    }

    @Test
    void duplicate_or_cross_provider_requirements_are_unknown_fail_closed() {
        RuntimeDependencyRequirement crossProvider = new RuntimeDependencyRequirement(
                ProviderImplementationId.of("ffmpeg.other"),
                FFMPEG,
                requirement().versionConstraint(),
                requirement().abiConstraint(),
                requirement().requiredFeatures(),
                requirement().requiredBuildRuntimeFlags());

        assertOnlyUnknownReason(
                RuntimeDependencyMatcher.match(
                        PROVIDER,
                        RUNTIME,
                        Optional.of(DEVICE),
                        List.of(requirement(), requirement()),
                        Optional.of(observation(observedDependency())),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
        assertOnlyUnknownReason(
                RuntimeDependencyMatcher.match(
                        PROVIDER,
                        RUNTIME,
                        Optional.of(DEVICE),
                        List.of(crossProvider),
                        Optional.of(observation(observedDependency())),
                        OBSERVED_AT),
                RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
    }

    @Test
    void multiple_dependency_failures_have_stable_code_then_coordinate_order() {
        RuntimeDependencyRequirement cuda = new RuntimeDependencyRequirement(
                PROVIDER,
                RuntimeDependencyCoordinate.of("cuda.runtime"),
                RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of("12.4")),
                Optional.empty(),
                List.of(),
                List.of());
        RuntimeDependencyMatchResult result = RuntimeDependencyMatcher.match(
                PROVIDER,
                RUNTIME,
                Optional.of(DEVICE),
                List.of(requirement(), cuda),
                Optional.of(observation(observedDependency(
                        "5.1", "libavcodec.59", List.of(), List.of()))),
                OBSERVED_AT);

        assertThat(result.status()).isEqualTo(RuntimeDependencyMatchStatus.CANNOT_MATCH);
        assertThat(result.reasons())
                .extracting(RuntimeDependencyMatchReason::code)
                .containsExactly(
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_MISSING,
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE,
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE,
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_FEATURE_MISSING,
                        RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING);
    }

    private static RuntimeDependencyRequirement requirement() {
        return requirement(PROVIDER);
    }

    private static RuntimeDependencyRequirement requirement(ProviderImplementationId provider) {
        return new RuntimeDependencyRequirement(
                provider,
                FFMPEG,
                RuntimeDependencyVersionConstraint.range(
                        RuntimeDependencyVersion.of("6.1"), RuntimeDependencyVersion.of("7")),
                Optional.of(RuntimeDependencyAbi.of("libavcodec.60")),
                List.of("codec.h264"),
                List.of("enable.gpl"));
    }

    private static RuntimeDependencyObservedDependency observedDependency() {
        return observedDependency(
                "6.1.2", "libavcodec.60", List.of("codec.h264"), List.of("enable.gpl"));
    }

    private static RuntimeDependencyObservedDependency observedDependency(
            String version, String abi, List<String> features, List<String> flags) {
        return new RuntimeDependencyObservedDependency(
                FFMPEG,
                RuntimeDependencyVersion.of(version),
                Optional.of(RuntimeDependencyAbi.of(abi)),
                features,
                flags);
    }

    private static RuntimeDependencyObservation observation(
            RuntimeDependencyObservedDependency... dependencies) {
        return new RuntimeDependencyObservation(
                PROVIDER,
                RUNTIME,
                Optional.of(DEVICE),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                OBSERVED_AT,
                EXPIRES_AT,
                List.of(dependencies));
    }

    private static RuntimeDependencyMatchResult match(
            RuntimeDependencyRequirement requirement, RuntimeDependencyObservation observation) {
        return RuntimeDependencyMatcher.match(
                PROVIDER,
                RUNTIME,
                Optional.of(DEVICE),
                List.of(requirement),
                Optional.of(observation),
                OBSERVED_AT);
    }

    private static RuntimeDependencyMatchResult matchAt(
            RuntimeDependencyObservation observation, Instant assessedAt) {
        return RuntimeDependencyMatcher.match(
                PROVIDER,
                RUNTIME,
                Optional.of(DEVICE),
                List.of(requirement()),
                Optional.of(observation),
                assessedAt);
    }

    private static void assertOnlyReason(
            RuntimeDependencyMatchResult result, RuntimeDependencyMatchReasonCode code) {
        assertThat(result.status()).isEqualTo(RuntimeDependencyMatchStatus.CANNOT_MATCH);
        assertThat(result.canMatch()).isFalse();
        assertThat(result.reasons()).extracting(RuntimeDependencyMatchReason::code).containsExactly(code);
    }

    private static void assertOnlyUnknownReason(
            RuntimeDependencyMatchResult result, RuntimeDependencyMatchReasonCode code) {
        assertThat(result.status()).isEqualTo(RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED);
        assertThat(result.canMatch()).isFalse();
        assertThat(result.reasons()).extracting(RuntimeDependencyMatchReason::code).containsExactly(code);
    }
}
