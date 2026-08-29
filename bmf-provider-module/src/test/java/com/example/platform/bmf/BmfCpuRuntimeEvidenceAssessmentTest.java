package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.RuntimeDependencyAbi;
import com.example.platform.workerfabric.domain.RuntimeDependencyFingerprint;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatchReason;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatchReasonCode;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatchStatus;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservedDependency;
import com.example.platform.workerfabric.domain.RuntimeDependencyProbeSchemaVersion;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersion;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BmfCpuRuntimeEvidenceAssessmentTest {

    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("bmf-runtime-a");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-30T08:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-30T08:05:00Z");

    @Test
    void accepts_exact_fresh_cpu_dependency_evidence_and_authoritative_fingerprint() {
        RuntimeDependencyObservation observation = exactObservation();

        BmfCpuRuntimeDependencyAssessment assessment = BmfCpuRuntimeEvidenceAssessor.assess(
                RUNTIME,
                Optional.of(observation),
                Optional.of(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT),
                OBSERVED_AT);

        assertThat(assessment.matchesRuntimeEvidence()).isTrue();
        assertThat(assessment.dependencyMatchResult().status())
                .isEqualTo(RuntimeDependencyMatchStatus.CAN_MATCH);
        assertThat(assessment.dependencyMatchResult().reasons()).isEmpty();
        assertThat(assessment.expectedFingerprint())
                .isSameAs(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT);
        assertThat(assessment.observedFingerprint())
                .contains(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT);
        assertThat(assessment.issues()).isEmpty();
    }

    @Test
    void fails_closed_for_every_wrong_or_incomplete_runtime_evidence_dimension() {
        assertDependencyReason(
                assess(Optional.empty(), exactFingerprint(), OBSERVED_AT),
                RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED,
                RuntimeDependencyMatchReasonCode.INCOMPLETE_CRITICAL_EVIDENCE);
        assertThat(assess(Optional.of(exactObservation()), Optional.empty(), OBSERVED_AT))
                .satisfies(assessment -> {
                    assertThat(assessment.matchesRuntimeEvidence()).isFalse();
                    assertThat(assessment.dependencyMatchResult().canMatch()).isTrue();
                    assertThat(assessment.issues())
                            .containsExactly(BmfCpuRuntimeEvidenceIssue.MISSING_FINGERPRINT);
                });
        assertDependencyReason(
                assess(Optional.of(exactObservation()), exactFingerprint(), EXPIRES_AT),
                RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED,
                RuntimeDependencyMatchReasonCode.STALE_OBSERVATION);
        assertDependencyReason(
                assess(Optional.of(observation(
                                ProviderImplementationId.of("bmf.cpu.other"),
                                RUNTIME,
                                Optional.empty(),
                                RuntimeDependencyProbeSchemaVersion.CURRENT,
                                exactDependencies())),
                        exactFingerprint(),
                        OBSERVED_AT),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.PROVIDER_IMPLEMENTATION_MISMATCH);
        assertDependencyReason(
                assess(Optional.of(observation(
                                BmfCpuProvider.IMPLEMENTATION_ID,
                                WorkerRuntimeId.of("bmf-runtime-other"),
                                Optional.empty(),
                                RuntimeDependencyProbeSchemaVersion.CURRENT,
                                exactDependencies())),
                        exactFingerprint(),
                        OBSERVED_AT),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.WORKER_RUNTIME_MISMATCH);
        assertDependencyReason(
                assess(Optional.of(observation(
                                BmfCpuProvider.IMPLEMENTATION_ID,
                                RUNTIME,
                                Optional.of(DeviceId.of("gpu-0")),
                                RuntimeDependencyProbeSchemaVersion.CURRENT,
                                exactDependencies())),
                        exactFingerprint(),
                        OBSERVED_AT),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.DEVICE_BINDING_MISMATCH);
        assertDependencyReason(
                assess(Optional.of(observation(
                                BmfCpuProvider.IMPLEMENTATION_ID,
                                RUNTIME,
                                Optional.empty(),
                                new RuntimeDependencyProbeSchemaVersion(2),
                                exactDependencies())),
                        exactFingerprint(),
                        OBSERVED_AT),
                RuntimeDependencyMatchStatus.UNKNOWN_FAIL_CLOSED,
                RuntimeDependencyMatchReasonCode.PROBE_SCHEMA_MISMATCH);

        assertDependencyReason(
                assessWithDependencies(exactDependencies().stream()
                        .filter(dependency -> !dependency.coordinate().value().equals("numpy"))
                        .toList()),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_MISSING);
        assertDependencyReason(
                assessWithDependencies(replaceDependency("python", dependency ->
                        new RuntimeDependencyObservedDependency(
                                dependency.coordinate(),
                                RuntimeDependencyVersion.of("3.11.9"),
                                dependency.abi(),
                                dependency.enabledFeatures(),
                                dependency.enabledBuildRuntimeFlags()))),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE);
        assertDependencyReason(
                assessWithDependencies(replaceDependency("ffmpeg", dependency ->
                        new RuntimeDependencyObservedDependency(
                                dependency.coordinate(),
                                dependency.version(),
                                Optional.of(RuntimeDependencyAbi.of("libavcodec.59")),
                                dependency.enabledFeatures(),
                                dependency.enabledBuildRuntimeFlags()))),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE);
        assertDependencyReason(
                assessWithDependencies(replaceDependency("bmf", dependency ->
                        new RuntimeDependencyObservedDependency(
                                dependency.coordinate(),
                                dependency.version(),
                                dependency.abi(),
                                List.of("python.enabled"),
                                dependency.enabledBuildRuntimeFlags()))),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_FEATURE_MISSING);
        assertDependencyReason(
                assessWithDependencies(replaceDependency("bmf", dependency ->
                        new RuntimeDependencyObservedDependency(
                                dependency.coordinate(),
                                dependency.version(),
                                dependency.abi(),
                                dependency.enabledFeatures(),
                                List.of("cuda.disabled")))),
                RuntimeDependencyMatchStatus.CANNOT_MATCH,
                RuntimeDependencyMatchReasonCode.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING);

        RuntimeDependencyFingerprint wrongFingerprint = RuntimeDependencyFingerprint.parseSha256(
                "sha256:0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(assess(Optional.of(exactObservation()), Optional.of(wrongFingerprint), OBSERVED_AT))
                .satisfies(assessment -> {
                    assertThat(assessment.matchesRuntimeEvidence()).isFalse();
                    assertThat(assessment.dependencyMatchResult().canMatch()).isTrue();
                    assertThat(assessment.observedFingerprint()).contains(wrongFingerprint);
                    assertThat(assessment.issues())
                            .containsExactly(BmfCpuRuntimeEvidenceIssue.FINGERPRINT_MISMATCH);
                });
    }

    private static RuntimeDependencyObservation exactObservation() {
        return observation(
                BmfCpuProvider.IMPLEMENTATION_ID,
                RUNTIME,
                Optional.empty(),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                exactDependencies());
    }

    private static List<RuntimeDependencyObservedDependency> exactDependencies() {
        return BmfCpuProvider.RUNTIME_DEPENDENCY_REQUIREMENTS.stream()
                        .map(requirement -> new RuntimeDependencyObservedDependency(
                                requirement.coordinate(),
                                requirement.versionConstraint().lowerBound(),
                                requirement.abiConstraint(),
                                requirement.requiredFeatures(),
                                requirement.requiredBuildRuntimeFlags()))
                        .toList();
    }

    private static RuntimeDependencyObservation observation(
            ProviderImplementationId provider,
            WorkerRuntimeId runtime,
            Optional<DeviceId> device,
            RuntimeDependencyProbeSchemaVersion schema,
            List<RuntimeDependencyObservedDependency> dependencies) {
        return new RuntimeDependencyObservation(
                provider, runtime, device, schema, OBSERVED_AT, EXPIRES_AT, dependencies);
    }

    private static BmfCpuRuntimeDependencyAssessment assessWithDependencies(
            List<RuntimeDependencyObservedDependency> dependencies) {
        return assess(Optional.of(observation(
                        BmfCpuProvider.IMPLEMENTATION_ID,
                        RUNTIME,
                        Optional.empty(),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        dependencies)),
                exactFingerprint(),
                OBSERVED_AT);
    }

    private static List<RuntimeDependencyObservedDependency> replaceDependency(
            String coordinate,
            java.util.function.UnaryOperator<RuntimeDependencyObservedDependency> replacement) {
        return exactDependencies().stream()
                .map(dependency -> dependency.coordinate().value().equals(coordinate)
                        ? replacement.apply(dependency)
                        : dependency)
                .toList();
    }

    private static Optional<RuntimeDependencyFingerprint> exactFingerprint() {
        return Optional.of(BmfCpuProvider.EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT);
    }

    private static BmfCpuRuntimeDependencyAssessment assess(
            Optional<RuntimeDependencyObservation> observation,
            Optional<RuntimeDependencyFingerprint> fingerprint,
            Instant assessedAt) {
        return BmfCpuRuntimeEvidenceAssessor.assess(RUNTIME, observation, fingerprint, assessedAt);
    }

    private static void assertDependencyReason(
            BmfCpuRuntimeDependencyAssessment assessment,
            RuntimeDependencyMatchStatus status,
            RuntimeDependencyMatchReasonCode reason) {
        assertThat(assessment.matchesRuntimeEvidence()).isFalse();
        assertThat(assessment.dependencyMatchResult().status()).isEqualTo(status);
        assertThat(assessment.dependencyMatchResult().reasons())
                .extracting(RuntimeDependencyMatchReason::code)
                .containsExactly(reason);
    }
}
