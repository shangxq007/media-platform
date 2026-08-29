package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeDependencyFingerprintTest {

    private static final Instant FIRST_OBSERVED = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    void typedFingerprintAcceptsAndPreservesCanonicalBmfDigest() {
        String canonical =
                "sha256:5ad7e1e40dd3cfa453960b829a6f61de7216c956638d06e7ad2cefe4be96dfd5";
        String payload = "5ad7e1e40dd3cfa453960b829a6f61de7216c956638d06e7ad2cefe4be96dfd5";

        RuntimeDependencyFingerprint fingerprint =
                RuntimeDependencyFingerprint.parseSha256(canonical);

        assertThat(fingerprint.value()).isEqualTo(payload);
        assertThat(fingerprint).isEqualTo(new RuntimeDependencyFingerprint(payload));
        assertThat(fingerprint.canonicalSha256()).isEqualTo(canonical);

        for (String malformed : List.of(
                payload,
                "SHA256:" + payload,
                "sha-256:" + payload,
                "sha256:" + payload.toUpperCase(),
                "sha256:" + "a".repeat(63),
                "sha256:" + "a".repeat(65),
                " sha256:" + payload,
                "sha256:" + payload + " ")) {
            assertThatIllegalArgumentException()
                    .as("reject %s", malformed)
                    .isThrownBy(() -> RuntimeDependencyFingerprint.parseSha256(malformed));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RuntimeDependencyFingerprint.parseSha256(null));
    }

    @Test
    void fingerprint_is_permutation_invariant_and_excludes_mutable_timestamps() {
        RuntimeDependencyObservedDependency ffmpeg = dependency(
                "ffmpeg.executable", "6.1.2", List.of("filter.zscale", "codec.h264"));
        RuntimeDependencyObservedDependency cuda =
                dependency("cuda.runtime", "12.4", List.of("api.cuda"));

        RuntimeDependencyFingerprint first = RuntimeDependencyFingerprint.from(
                observation(FIRST_OBSERVED, List.of(ffmpeg, cuda)));
        RuntimeDependencyFingerprint reorderedAndReobserved = RuntimeDependencyFingerprint.from(
                observation(FIRST_OBSERVED.plus(Duration.ofHours(2)), List.of(cuda, ffmpeg)));

        assertThat(first).isEqualTo(reorderedAndReobserved);
        assertThat(first.value()).matches("[0-9a-f]{64}");
    }

    @Test
    void fingerprint_changes_when_exact_bundle_content_changes() {
        RuntimeDependencyObservation baseline = observation(
                FIRST_OBSERVED,
                List.of(dependency("ffmpeg.executable", "6.1.2", List.of("codec.h264"))));
        RuntimeDependencyObservation versionChanged = observation(
                FIRST_OBSERVED,
                List.of(dependency("ffmpeg.executable", "6.1.3", List.of("codec.h264"))));
        RuntimeDependencyObservation featureChanged = observation(
                FIRST_OBSERVED,
                List.of(dependency("ffmpeg.executable", "6.1.2", List.of("codec.hevc"))));

        assertThat(RuntimeDependencyFingerprint.from(baseline))
                .isNotEqualTo(RuntimeDependencyFingerprint.from(versionChanged))
                .isNotEqualTo(RuntimeDependencyFingerprint.from(featureChanged));
    }

    @Test
    void fingerprint_exposes_only_the_digest_not_host_paths_or_probe_secrets() {
        RuntimeDependencyObservation privatePathRuntime = new RuntimeDependencyObservation(
                ProviderImplementationId.of("provider.private-token"),
                WorkerRuntimeId.of("/opt/private/runtime/token"),
                Optional.empty(),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                FIRST_OBSERVED,
                FIRST_OBSERVED.plusSeconds(60),
                List.of(dependency("ffmpeg.executable", "6.1.2", List.of())));

        RuntimeDependencyFingerprint fingerprint = RuntimeDependencyFingerprint.from(privatePathRuntime);

        assertThat(fingerprint.value())
                .matches("[0-9a-f]{64}")
                .doesNotContain("opt", "private", "token", "ffmpeg");
        assertThat(fingerprint.toString()).isEqualTo(fingerprint.value());
    }

    private static RuntimeDependencyObservation observation(
            Instant observedAt, List<RuntimeDependencyObservedDependency> dependencies) {
        return new RuntimeDependencyObservation(
                ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1"),
                WorkerRuntimeId.of("runtime-a"),
                Optional.of(DeviceId.of("gpu-0")),
                new RuntimeDependencyProbeSchemaVersion(2),
                observedAt,
                observedAt.plusSeconds(300),
                dependencies);
    }

    private static RuntimeDependencyObservedDependency dependency(
            String coordinate, String version, List<String> features) {
        return new RuntimeDependencyObservedDependency(
                RuntimeDependencyCoordinate.of(coordinate),
                RuntimeDependencyVersion.of(version),
                Optional.of(RuntimeDependencyAbi.of("abi.1")),
                features,
                List.of("enable.shared"));
    }
}
