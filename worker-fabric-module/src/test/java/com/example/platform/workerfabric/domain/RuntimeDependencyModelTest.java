package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeDependencyModelTest {

    private static final ProviderImplementationId PROVIDER =
            ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1");
    private static final RuntimeDependencyCoordinate FFMPEG =
            RuntimeDependencyCoordinate.of("ffmpeg.executable");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-29T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T10:05:00Z");

    @Test
    void requirement_is_immutable_and_canonicalizes_feature_and_flag_order() {
        RuntimeDependencyRequirement requirement = new RuntimeDependencyRequirement(
                PROVIDER,
                FFMPEG,
                RuntimeDependencyVersionConstraint.range(
                        RuntimeDependencyVersion.of("6.1"), RuntimeDependencyVersion.of("7")),
                Optional.of(RuntimeDependencyAbi.of("libavcodec.60")),
                List.of("filter.zscale", "codec.h264"),
                List.of("enable.libx264", "enable.gpl"));

        assertThat(requirement.requiredFeatures()).containsExactly("codec.h264", "filter.zscale");
        assertThat(requirement.requiredBuildRuntimeFlags())
                .containsExactly("enable.gpl", "enable.libx264");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> requirement.requiredFeatures().add("codec.av1"));
        assertThat(requirement.versionConstraint().matches(RuntimeDependencyVersion.of("6.1.2"))).isTrue();
        assertThat(requirement.versionConstraint().matches(RuntimeDependencyVersion.of("7"))).isFalse();
    }

    @Test
    void exact_and_range_versions_are_bounded_and_reject_invalid_ranges() {
        assertThat(RuntimeDependencyVersion.of("6.1.0").value()).isEqualTo("6.1");
        assertThat(RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of("6.1"))
                        .matches(RuntimeDependencyVersion.of("6.1.0")))
                .isTrue();

        for (String invalid : List.of("", "v6.1", "06.1", "6..1", "6.1/opt", "6.1-SNAPSHOT")) {
            assertThatIllegalArgumentException()
                    .as("reject version %s", invalid)
                    .isThrownBy(() -> RuntimeDependencyVersion.of(invalid));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> RuntimeDependencyVersionConstraint.range(
                RuntimeDependencyVersion.of("7"), RuntimeDependencyVersion.of("6")));
        assertThatIllegalArgumentException().isThrownBy(() -> RuntimeDependencyVersionConstraint.range(
                RuntimeDependencyVersion.of("6"), RuntimeDependencyVersion.of("6.0")));
    }

    @Test
    void observation_is_freshness_bound_sorted_immutable_and_rejects_duplicate_coordinates() {
        RuntimeDependencyObservedDependency cuda = observed("cuda.runtime", "12.4");
        RuntimeDependencyObservedDependency ffmpeg = observed("ffmpeg.executable", "6.1.2");

        RuntimeDependencyObservation observation = new RuntimeDependencyObservation(
                PROVIDER,
                WorkerRuntimeId.of("runtime-a"),
                Optional.of(DeviceId.of("gpu-0")),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                OBSERVED_AT,
                EXPIRES_AT,
                List.of(ffmpeg, cuda));

        assertThat(observation.dependencies())
                .extracting(entry -> entry.coordinate().value())
                .containsExactly("cuda.runtime", "ffmpeg.executable");
        assertThat(observation.isFreshAt(OBSERVED_AT)).isTrue();
        assertThat(observation.isFreshAt(EXPIRES_AT.minusNanos(1))).isTrue();
        assertThat(observation.isFreshAt(EXPIRES_AT)).isFalse();
        assertThat(observation.isFreshAt(OBSERVED_AT.minusNanos(1))).isFalse();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> observation.dependencies().add(ffmpeg));
        assertThatIllegalArgumentException().isThrownBy(() -> new RuntimeDependencyObservation(
                PROVIDER,
                WorkerRuntimeId.of("runtime-a"),
                Optional.empty(),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                OBSERVED_AT,
                EXPIRES_AT,
                List.of(ffmpeg, ffmpeg)));
    }

    @Test
    void construction_rejects_noncanonical_attributes_and_invalid_freshness_windows() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RuntimeDependencyRequirement(
                PROVIDER,
                FFMPEG,
                RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of("6.1")),
                Optional.empty(),
                List.of("Codec.H264"),
                List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RuntimeDependencyRequirement(
                PROVIDER,
                FFMPEG,
                RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of("6.1")),
                Optional.empty(),
                List.of("codec.h264", "codec.h264"),
                List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RuntimeDependencyObservation(
                PROVIDER,
                WorkerRuntimeId.of("runtime-a"),
                Optional.empty(),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                EXPIRES_AT,
                EXPIRES_AT,
                List.of(observed("ffmpeg.executable", "6.1"))));
    }

    private static RuntimeDependencyObservedDependency observed(String coordinate, String version) {
        return new RuntimeDependencyObservedDependency(
                RuntimeDependencyCoordinate.of(coordinate),
                RuntimeDependencyVersion.of(version),
                Optional.empty(),
                List.of("codec.h264", "filter.zscale"),
                List.of("enable.gpl", "enable.libx264"));
    }
}
