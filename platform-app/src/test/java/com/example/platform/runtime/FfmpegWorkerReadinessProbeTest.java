package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.BubblewrapSandboxDetection;
import com.example.platform.workerfabric.domain.RuntimeEnvironmentAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1-1A.4 Gate A: the concrete ffmpeg worker readiness probe observes two independent signals for
 * real — a write/read-back environment probe and the production-shape bubblewrap capability probe.
 * No testcontainers are started.
 */
class FfmpegWorkerReadinessProbeTest {

    @TempDir Path temp;

    @Test
    void environmentReadinessIsARealWriteProbeOverTheConfiguredExecutionRoots() throws IOException {
        Path work = temp.resolve("work");
        Path workspace = temp.resolve("workspace");
        Path staging = temp.resolve("staging");

        FfmpegWorkerReadinessProbe probe = FfmpegWorkerReadinessProbe.of(
                work.toString(), workspace.toString(), staging.toString(), "/bin/true");

        assertThat(probe.observeEnvironmentReadiness())
                .isEqualTo(RuntimeEnvironmentAvailability.AVAILABLE);
        // The probe really visited the sandbox sub-roots and left no probe file behind.
        assertThat(Files.isDirectory(workspace.resolve(".sandbox-tmp"))).isTrue();
        assertThat(Files.isDirectory(workspace.resolve(".sandbox-output"))).isTrue();
        assertThat(Files.list(workspace))
                .noneMatch(path -> path.getFileName().toString().startsWith(".platform-readiness-probe-"));
    }

    @Test
    void environmentReadinessIsUnavailableWhenAConfiguredRootCannotBeUsed() throws IOException {
        Path occupiedStaging = temp.resolve("staging-is-a-file");
        Files.writeString(occupiedStaging, "not a directory");

        FfmpegWorkerReadinessProbe probe = FfmpegWorkerReadinessProbe.of(
                temp.resolve("work").toString(),
                temp.resolve("workspace").toString(),
                occupiedStaging.toString(),
                "/bin/true");

        assertThat(probe.observeEnvironmentReadiness())
                .isEqualTo(RuntimeEnvironmentAvailability.UNAVAILABLE);
    }

    @Test
    void sandboxReadinessIsUnavailableWhenTheConfiguredBinaryCannotEnforceTheProductionShape() {
        FfmpegWorkerReadinessProbe probe = FfmpegWorkerReadinessProbe.of(
                temp.resolve("work").toString(),
                temp.resolve("workspace").toString(),
                temp.resolve("staging").toString(),
                // A real executable that ignores arguments: the production-shape probe runs and fails.
                "/bin/true");

        assertThat(probe.observeSandboxReadiness())
                .isEqualTo(SandboxRuntimeAvailability.UNAVAILABLE);
    }

    @Test
    void sandboxMappingNeverReportsAvailableWithoutAProductionShapeLauncher() {
        // Not installed and installed-but-probe-failed both fail closed as UNAVAILABLE.
        assertThat(FfmpegWorkerReadinessProbe.sandboxAvailability(
                        new BubblewrapSandboxDetection(false, Optional.empty(), "absent")))
                .isEqualTo(SandboxRuntimeAvailability.UNAVAILABLE);
        assertThat(FfmpegWorkerReadinessProbe.sandboxAvailability(
                        new BubblewrapSandboxDetection(true, Optional.empty(), "probe failed")))
                .isEqualTo(SandboxRuntimeAvailability.UNAVAILABLE);
    }

    @Test
    void sandboxReadinessIsAvailableOnlyWhenTheRealProductionShapeProbePasses() {
        Path bwrap = Path.of("/usr/bin/bwrap");
        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect(bwrap);
        Assumptions.assumeTrue(
                detection.launcher().isPresent(),
                "no working bubblewrap in this environment; the real AVAILABLE observation is skipped");

        FfmpegWorkerReadinessProbe probe = FfmpegWorkerReadinessProbe.of(
                temp.resolve("work").toString(),
                temp.resolve("workspace").toString(),
                temp.resolve("staging").toString(),
                bwrap.toString());

        assertThat(probe.observeSandboxReadiness()).isEqualTo(SandboxRuntimeAvailability.AVAILABLE);
    }
}
