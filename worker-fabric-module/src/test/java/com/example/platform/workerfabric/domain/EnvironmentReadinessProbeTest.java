package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P1-1A.4 Gate A: the bounded-V1 Environment Readiness probe observes real writability. */
class EnvironmentReadinessProbeTest {

    @TempDir Path temp;

    @Test
    void writableRootsAreAvailableAndLeaveNoProbeArtifacts() throws IOException {
        Path workRoot = temp.resolve("work-root");
        Path workspace = temp.resolve("workspace");
        Path staging = temp.resolve("staging");

        var result = new EnvironmentReadinessProbe(List.of(workRoot, workspace, staging)).probe();

        assertThat(result.availability()).isEqualTo(RuntimeEnvironmentAvailability.AVAILABLE);
        assertThat(result.diagnostics()).isEmpty();
        // real observation, and no user files / probe leftovers are left behind
        for (Path root : List.of(workRoot, workspace, staging)) {
            try (Stream<Path> entries = Files.list(root)) {
                assertThat(entries).noneMatch(path -> path.getFileName().toString().startsWith(".platform-readiness-probe-"));
            }
        }
    }

    @Test
    void aRootOccupiedByAFileIsUnavailableNotAvailable() throws IOException {
        Path occupied = temp.resolve("occupied-root");
        Files.writeString(occupied, "not a directory");

        var result = new EnvironmentReadinessProbe(List.of(occupied)).probe();

        assertThat(result.availability()).isEqualTo(RuntimeEnvironmentAvailability.UNAVAILABLE);
        assertThat(result.diagnostics()).isNotEmpty();
    }

    @Test
    void probeRequiresAtLeastOneRoot() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new EnvironmentReadinessProbe(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
