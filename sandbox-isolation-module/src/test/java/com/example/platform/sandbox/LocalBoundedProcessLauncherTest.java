package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
class LocalBoundedProcessLauncherTest {

    @TempDir Path temp;

    @Test
    void local_capabilities_describe_only_mechanics_the_local_process_adapter_enforces() {
        SandboxRuntimeCapabilities detected = LocalSandboxCapabilityDetector.detect();

        assertThat(detected.capabilities()).contains(
                SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.BEST_EFFORT_DESCENDANT_CLEANUP);
        assertThat(detected.capabilities()).doesNotContain(
                SandboxCapability.PROCESS_TREE_CONTAINMENT,
                SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.NETWORK_NONE,
                SandboxCapability.SECRET_INJECTION,
                SandboxCapability.HOST_EXPOSURE_DENIAL,
                SandboxCapability.DEVICE_NONE);
    }

    @Test
    void mandatory_hard_isolation_fails_closed_for_the_local_process_adapter() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(
                        Set.of("/bin/true"), "/bin/true", java.util.List.of(), Duration.ofSeconds(1)),
                FilesystemPolicy.exact(Set.of(), workspace, workspace.resolve("tmp"),
                        workspace.resolve("out"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(1024),
                DeviceExposurePolicy.none());

        SandboxResolution resolution = SandboxExecutionResolver.resolve(
                requirement, LocalSandboxCapabilityDetector.detect());

        assertThat(resolution).isInstanceOf(SandboxResolution.Rejected.class);
        assertThat(((SandboxResolution.Rejected) resolution).failure().missingCapabilities())
                .contains(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.NETWORK_NONE,
                        SandboxCapability.HOST_EXPOSURE_DENIAL,
                        SandboxCapability.DEVICE_NONE);
    }

    @Test
    void neutral_local_port_selects_only_a_real_probed_bubblewrap_launcher() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("local-port-workspace"));

        SandboxExecutionResult result = LocalSandboxProcess.execute(
                java.util.List.of("/bin/true"), workspace, workspace, Set.of(), Map.of(),
                Duration.ofSeconds(1), 1024, SandboxCancellation.never());

        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        if (detection.launcher().isPresent()) {
            assertThat(result.failure()).as(result.toString()).isEmpty();
            assertThat(result.exitCode()).hasValue(0);
        } else {
            assertThat(result.exitCode()).isEmpty();
            assertThat(result.failure()).isPresent();
            assertThat(result.failure().orElseThrow().code())
                    .isEqualTo(SandboxFailureCode.SANDBOX_UNAVAILABLE);
        }
    }

    @Test
    void best_effort_descendant_cleanup_is_idempotent_after_process_exit() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("/bin/true");
        builder.environment().clear();
        Process process = builder.start();
        process.waitFor();

        SandboxCleanupObservation first = LocalBoundedProcessLauncher.terminateTree(process);
        SandboxCleanupObservation second = LocalBoundedProcessLauncher.terminateTree(process);

        assertThat(first.completed()).isTrue();
        assertThat(second.completed()).isTrue();
        assertThat(second.survivors()).isEmpty();
    }

    @Test
    void path_validation_still_rejects_traversal_and_symlink_escape() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("paths"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path link = workspace.resolve("link");
        Files.createSymbolicLink(link, outside);

        assertThat(FilesystemPathValidator.validateWorkingDirectory(workspace, workspace.resolve("../outside")))
                .contains(SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION);
        assertThat(FilesystemPathValidator.validateWithin(workspace, link.resolve("created-later")))
                .contains(SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION);
    }
}
