package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.platform.testsupport.Phase17SandboxConformance.requireCapability;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BubblewrapSandboxProcessLauncherTest {
    private static final Path BWRAP = Path.of("/usr/bin/bwrap");

    @TempDir Path temp;

    @Test
    void prepared_command_must_start_with_the_launcher_probed_bwrap_executable() {
        BubblewrapSandboxProcessLauncher launcher = preparedCommandLauncher();

        assertThatThrownBy(() -> launcher.launchPreparedCommand(
                List.of("/bin/true", "--unshare-all", "--die-with-parent"),
                temp, Duration.ofSeconds(1), 1024, SandboxCancellation.never()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bwrap executable");
    }

    @Test
    void prepared_command_missing_namespace_unsharing_is_rejected() {
        BubblewrapSandboxProcessLauncher launcher = preparedCommandLauncher();

        assertThatThrownBy(() -> launcher.launchPreparedCommand(
                List.of(BWRAP.toString(), "--die-with-parent", "/bin/true"),
                temp, Duration.ofSeconds(1), 1024, SandboxCancellation.never()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--unshare-all");
    }

    @Test
    void prepared_command_missing_parent_lifetime_binding_is_rejected() {
        BubblewrapSandboxProcessLauncher launcher = preparedCommandLauncher();

        assertThatThrownBy(() -> launcher.launchPreparedCommand(
                List.of(BWRAP.toString(), "--unshare-all", "/bin/true"),
                temp, Duration.ofSeconds(1), 1024, SandboxCancellation.never()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--die-with-parent");
    }

    @Test
    void prepared_command_that_declares_the_launcher_policy_executes_under_bubblewrap()
            throws Exception {
        requireCapability(Files.isRegularFile(BWRAP) && Files.isExecutable(BWRAP),
                "/usr/bin/bwrap is not installed");
        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect(BWRAP);
        requireCapability(detection.launcher().isPresent(), detection.diagnostic());
        BubblewrapSandboxProcessLauncher launcher = detection.launcher().orElseThrow();
        Path work = Files.createDirectory(temp.resolve("prepared-work"));

        SandboxExecutionResult result = launcher.launchPreparedCommand(
                List.of(BWRAP.toString(), "--ro-bind", "/", "/", "--dev", "/dev",
                        "--proc", "/proc", "--tmpfs", "/tmp", "--unshare-all",
                        "--die-with-parent", "/usr/bin/true"),
                work, Duration.ofSeconds(10), 4096, SandboxCancellation.never());

        assertThat(result.failure()).as(result.toString()).isEmpty();
        assertThat(result.exitCode()).hasValue(0);
    }

    @Test
    void detector_probes_the_configured_binary_and_rejects_a_non_bwrap_path() throws Exception {
        Path notBubblewrap = Files.writeString(temp.resolve("not-bwrap"), "#!/bin/sh\nexit 1\n");
        notBubblewrap.toFile().setExecutable(true);

        BubblewrapSandboxDetection detection =
                BubblewrapSandboxCapabilityDetector.detect(notBubblewrap);

        assertThat(detection.bubblewrapInstalled()).isTrue();
        assertThat(detection.launcher()).isEmpty();
    }

    @Test
    void detector_falls_back_to_the_canonical_bwrap_when_the_configured_path_is_absent() {
        requireCapability(Files.isRegularFile(BWRAP) && Files.isExecutable(BWRAP),
                "/usr/bin/bwrap is not installed");

        BubblewrapSandboxDetection detection =
                BubblewrapSandboxCapabilityDetector.detect(temp.resolve("absent-bwrap"));

        assertThat(detection.bubblewrapInstalled()).isTrue();
        assertThat(detection.launcher()).as(detection.diagnostic()).isPresent();
    }

    private static BubblewrapSandboxProcessLauncher preparedCommandLauncher() {
        return new BubblewrapSandboxProcessLauncher(
                BWRAP, SandboxRuntimeCapabilities.unavailable("prepared-command-test"));
    }

    @Test
    void production_argv_has_only_the_approved_mount_namespace_and_exact_environment()
            throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path temporary = Files.createDirectory(workspace.resolve(".sandbox-tmp"));
        Path output = Files.createDirectory(workspace.resolve(".sandbox-output"));
        Path input = Files.writeString(temp.resolve("external-input"), "input");
        ProcessRequirement process = ProcessRequirement.of(
                Set.of("/usr/bin/cat"), "/usr/bin/cat",
                List.of(input.toString(), "--output=" + output.resolve("result")),
                Duration.ofSeconds(5));
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                process, FilesystemPolicy.exact(
                        Set.of(input), workspace, temporary, output, workspace),
                NetworkPolicy.none(),
                EnvironmentPolicy.exact(Map.of("PATH", "/usr/bin:/bin", "LANG", "C")),
                SecretExposure.none(), PrivilegePolicy.unprivileged(),
                ResourceEnforcementLimits.captureOnly(4096), DeviceExposurePolicy.none());
        EffectiveSandboxExecutionSpecification specification =
                EffectiveSandboxExecutionSpecification.resolved(requirement,
                        SandboxRuntimeCapabilities.unavailable("argv-test"));

        List<String> command = BubblewrapSandboxProcessLauncher.buildCommand(
                Path.of("/usr/bin/bwrap"), specification);

        assertThat(command).containsSubsequence(
                "/usr/bin/bwrap", "--unshare-all", "--die-with-parent", "--new-session")
                .containsSubsequence("--proc", "/proc", "--dev", "/dev")
                .containsSubsequence("--ro-bind", workspace.toRealPath().toString(), "/workspace")
                .containsSubsequence("--bind", temporary.toRealPath().toString(),
                        "/workspace/.sandbox-tmp")
                .containsSubsequence("--bind", output.toRealPath().toString(),
                        "/workspace/.sandbox-output")
                .containsSubsequence("--ro-bind", input.toRealPath().toString(),
                        "/sandbox-inputs/input-0")
                .containsSubsequence("--remount-ro", "/sandbox-inputs")
                .containsSubsequence("--chdir", "/workspace", "--clearenv")
                .containsSubsequence("--setenv", "LANG", "C")
                .containsSubsequence("--setenv", "PATH", "/usr/bin:/bin")
                .endsWith("/usr/bin/cat", "/sandbox-inputs/input-0",
                        "--output=/workspace/.sandbox-output/result")
                .doesNotContain("--share-net", "/home", "/etc",
                        "/run", "/var/run/docker.sock");
        assertThat(command).doesNotContainSubsequence("--bind", "/", "/");
    }

    @Test
    void approved_external_static_executable_is_mounted_read_only_and_translated() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("external-executable-workspace"));
        Path temporary = Files.createDirectory(workspace.resolve(".sandbox-tmp"));
        Path output = Files.createDirectory(workspace.resolve(".sandbox-output"));
        Path executable = Files.writeString(temp.resolve("ffmpeg-static"), "fixture");
        executable.toFile().setExecutable(true);
        ProcessRequirement process = ProcessRequirement.of(
                Set.of(executable.toString()), executable.toString(), List.of("-version"),
                Duration.ofSeconds(5));
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                process,
                FilesystemPolicy.exact(
                        Set.of(executable), workspace, temporary, output, workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(4096),
                DeviceExposurePolicy.none());
        EffectiveSandboxExecutionSpecification specification =
                EffectiveSandboxExecutionSpecification.resolved(
                        requirement, SandboxRuntimeCapabilities.unavailable("argv-test"));

        List<String> command = BubblewrapSandboxProcessLauncher.buildCommand(
                Path.of("/usr/bin/bwrap"), specification);

        assertThat(command)
                .containsSubsequence("--ro-bind", executable.toRealPath().toString(),
                        "/sandbox-inputs/input-0")
                .endsWith("/sandbox-inputs/input-0", "-version");
    }

    @Test
    void sensitive_config_input_is_rejected_before_bubblewrap_launch() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("sensitive-workspace"));
        Path temporary = workspace.resolve(".sandbox-tmp");
        Path output = workspace.resolve(".sandbox-output");
        Path config = Files.createDirectories(temp.resolve(".config"));
        Path secret = Files.writeString(config.resolve("credential"), "not-mounted");
        SandboxRuntimeCapabilities capabilities = SandboxRuntimeCapabilities.detected(
                EnumSet.of(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.WALL_CLOCK_TIMEOUT,
                        SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.NETWORK_NONE,
                        SandboxCapability.ENVIRONMENT_CLEARING,
                        SandboxCapability.BOUNDED_CAPTURE,
                        SandboxCapability.UNPRIVILEGED_EXECUTION,
                        SandboxCapability.HOST_EXPOSURE_DENIAL,
                        SandboxCapability.DEVICE_NONE),
                "unit-test-bubblewrap", Instant.EPOCH);
        BubblewrapSandboxProcessLauncher launcher = new BubblewrapSandboxProcessLauncher(
                Path.of("/usr/bin/bwrap"), capabilities);
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(Set.of("/usr/bin/true"), "/usr/bin/true", List.of(),
                        Duration.ofSeconds(1)),
                FilesystemPolicy.exact(Set.of(secret), workspace, temporary, output, workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(1024),
                DeviceExposurePolicy.none());

        SandboxExecutionResult result = launcher.launchResolved(
                requirement, SandboxCancellation.never());

        assertThat(result.exitCode()).isEmpty();
        assertThat(result.failure()).isPresent();
        assertThat(result.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION);
    }

    @Test
    void repository_root_is_rejected_as_a_host_workspace_mount() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("repository-workspace"));
        Files.writeString(workspace.resolve(".git"), "gitdir: elsewhere");
        SandboxRuntimeCapabilities capabilities = SandboxRuntimeCapabilities.detected(
                EnumSet.of(
                        SandboxCapability.PROCESS_TREE_CONTAINMENT,
                        SandboxCapability.WALL_CLOCK_TIMEOUT,
                        SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                        SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                        SandboxCapability.NETWORK_NONE,
                        SandboxCapability.ENVIRONMENT_CLEARING,
                        SandboxCapability.BOUNDED_CAPTURE,
                        SandboxCapability.UNPRIVILEGED_EXECUTION,
                        SandboxCapability.HOST_EXPOSURE_DENIAL,
                        SandboxCapability.DEVICE_NONE),
                "unit-test-bubblewrap", Instant.EPOCH);
        BubblewrapSandboxProcessLauncher launcher = new BubblewrapSandboxProcessLauncher(
                Path.of("/usr/bin/bwrap"), capabilities);
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(Set.of("/usr/bin/true"), "/usr/bin/true", List.of(),
                        Duration.ofSeconds(1)),
                FilesystemPolicy.exact(Set.of(), workspace, workspace.resolve(".sandbox-tmp"),
                        workspace.resolve(".sandbox-output"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(1024),
                DeviceExposurePolicy.none());

        SandboxExecutionResult result = launcher.launchResolved(
                requirement, SandboxCancellation.never());

        assertThat(result.exitCode()).isEmpty();
        assertThat(result.failure()).isPresent();
        assertThat(result.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION);
    }

    @Test
    void timeout_primary_and_typed_cleanup_failure_remain_separately_observable() {
        SandboxFailure timeout = SandboxFailure.of(
                SandboxFailureCode.PROCESS_TIMEOUT, "timed out", Set.of());
        SandboxCleanupObservation cleanup = SandboxCleanupObservation.failed(
                2, List.of(123L), "descendant remains");

        assertThat(BubblewrapSandboxProcessLauncher.selectResultFailure(timeout, cleanup))
                .contains(timeout);
        assertThat(cleanup.failure()).isPresent();
        assertThat(cleanup.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.SANDBOX_CLEANUP_FAILED);
        assertThat(BubblewrapSandboxProcessLauncher.selectResultFailure(null, cleanup))
                .contains(cleanup.failure().orElseThrow());
    }
}
