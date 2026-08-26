package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerEngineBehaviorTest {
    @TempDir Path temp;

    @Test
    void identifies_podman_compatibility_from_behavior_instead_of_docker_filename() throws Exception {
        Path docker = temp.resolve("docker");
        Files.writeString(docker, "#!/bin/sh\n"
                + "echo 'Emulate Docker CLI using podman.' >&2\n"
                + "echo 'podman version 5.4.2'\n");
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwx------"));

        assertThat(ContainerSandboxCapabilityDetector.engineKind(
                docker, Map.of("PATH", "/usr/bin:/bin")))
                .contains(ContainerEngineConfiguration.Kind.PODMAN);
    }

    @Test
    void generates_only_engine_supported_tmpfs_ownership_options() {
        assertThat(ContainerSandboxProcessLauncher.tmpfsOption(
                ContainerEngineConfiguration.Kind.PODMAN, "/sandbox/workspace/tmp",
                16L << 20, "1000", "1000"))
                .isEqualTo("--tmpfs=/sandbox/workspace/tmp:rw,noexec,nosuid,nodev,"
                        + "size=16777216,mode=1777")
                .doesNotContain("uid=", "gid=");

        assertThat(ContainerSandboxProcessLauncher.tmpfsOption(
                ContainerEngineConfiguration.Kind.DOCKER, "/sandbox/workspace/tmp",
                16L << 20, "1000", "1000"))
                .isEqualTo("--tmpfs=/sandbox/workspace/tmp:rw,noexec,nosuid,nodev,"
                        + "size=16777216,mode=0700,uid=1000,gid=1000");
    }

    @Test
    void generates_engine_specific_immediate_forced_removal_argv() {
        ContainerEngineConfiguration podman = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, Path.of("/usr/bin/podman"),
                "unused:test", Map.of());
        ContainerEngineConfiguration docker = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.DOCKER, Path.of("/usr/bin/docker"),
                "unused:test", Map.of());

        assertThat(podman.immediateRemovalCommand("phase17"))
                .containsExactly(
                        "/usr/bin/podman", "rm", "--force", "--time=0", "phase17");
        assertThat(docker.immediateRemovalCommand("phase17"))
                .containsExactly("/usr/bin/docker", "rm", "--force", "phase17");
    }

    @Test
    void unavailable_swap_controller_does_not_create_swap_argv_or_false_memory_evidence()
            throws Exception {
        List<List<String>> exactProductionArguments = new ArrayList<>();

        Set<SandboxCapability> detected =
                ContainerSandboxCapabilityDetector.detectResourceCapabilities(limits -> {
                    List<String> arguments =
                            ContainerSandboxProcessLauncher.resourceArguments(limits);
                    exactProductionArguments.add(arguments);
                    // Simulates Podman rejecting --memory because its derived swap setup cannot
                    // open memory.swap.max on this rootless cgroup delegation.
                    return arguments.stream().noneMatch(argument -> argument.startsWith("--memory="));
                });

        assertThat(exactProductionArguments).hasSize(4);
        assertThat(exactProductionArguments).allSatisfy(arguments ->
                assertThat(arguments).noneMatch(argument -> argument.contains("memory-swap")));
        assertThat(exactProductionArguments).containsExactly(
                List.of("--cpus=1.0"),
                List.of("--memory=67108864"),
                List.of("--pids-limit=16"),
                List.of("--ulimit=nofile=32:32"));
        assertThat(detected).containsExactlyInAnyOrder(
                SandboxCapability.CPU_COUNT_LIMIT,
                SandboxCapability.PROCESS_COUNT_LIMIT,
                SandboxCapability.OPEN_FILE_LIMIT);
        assertThat(detected).doesNotContain(SandboxCapability.MEMORY_LIMIT);

        EnumSet<SandboxCapability> runtimeCapabilities = EnumSet.of(
                SandboxCapability.PROCESS_TREE_CONTAINMENT,
                SandboxCapability.WALL_CLOCK_TIMEOUT,
                SandboxCapability.FILESYSTEM_PATH_VALIDATION,
                SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.NETWORK_NONE,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.BOUNDED_CAPTURE,
                SandboxCapability.UNPRIVILEGED_EXECUTION,
                SandboxCapability.HOST_EXPOSURE_DENIAL,
                SandboxCapability.DEVICE_NONE);
        runtimeCapabilities.addAll(detected);
        Path workspace = Files.createDirectory(temp.resolve("resource-workspace"));
        Path input = Files.writeString(temp.resolve("resource-input"), "input");
        ResourceEnforcementLimits mandatoryMemory = new ResourceEnforcementLimits(
                Optional.empty(), OptionalLong.of(64L << 20), OptionalInt.empty(),
                OptionalInt.empty(), OptionalLong.empty(), OptionalLong.empty(), 4096);
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(Set.of("/usr/bin/env"), "/usr/bin/env", List.of(),
                        Duration.ofSeconds(1)),
                FilesystemPolicy.exact(Set.of(input), workspace, workspace.resolve("tmp"),
                        workspace.resolve("out"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), mandatoryMemory, DeviceExposurePolicy.none());

        SandboxResolution.Rejected rejected = (SandboxResolution.Rejected)
                SandboxExecutionResolver.resolve(requirement,
                        SandboxRuntimeCapabilities.available(runtimeCapabilities));
        assertThat(rejected.failure().missingCapabilities())
                .containsExactly(SandboxCapability.MEMORY_LIMIT);
    }

    @Test
    void forced_removal_retries_running_status_until_podman_reports_not_found() throws Exception {
        Path state = temp.resolve("inspect-count");
        Path log = temp.resolve("removal-log");
        Path engine = fakeEngine("retrying-engine", """
                #!/bin/sh
                if [ "$1" = "rm" ]; then
                  echo rm >> "$FAKE_ENGINE_LOG"
                  exit 0
                fi
                if [ "$1" = "container" ] && [ "$2" = "inspect" ]; then
                  count=0
                  if [ -f "$FAKE_ENGINE_STATE" ]; then count=$(sed -n '1p' "$FAKE_ENGINE_STATE"); fi
                  count=$((count + 1))
                  echo "$count" > "$FAKE_ENGINE_STATE"
                  if [ "$count" -lt 3 ]; then
                    echo '  "REMOVING"  '
                    exit 0
                  fi
                  echo 'Error: no container with name or ID "phase17" found: no such container' >&2
                  exit 125
                fi
                exit 125
                """);
        ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, engine, "unused:test",
                Map.of("PATH", "/usr/bin:/bin", "FAKE_ENGINE_STATE", state.toString(),
                        "FAKE_ENGINE_LOG", log.toString()));

        ContainerSandboxProcessLauncher.ContainerRemovalObservation removal =
                ContainerSandboxProcessLauncher.removeContainer(
                        configuration, "phase17", Duration.ofSeconds(1), Duration.ofMillis(5));

        assertThat(removal.completed()).isTrue();
        assertThat(removal.removalCommandSucceeded()).isTrue();
        assertThat(removal.attempts()).isEqualTo(3);
        assertThat(removal.lastStatus()).isEqualTo("removed");
        assertThat(Files.readString(state).trim()).isEqualTo("3");
        assertThat(Files.readAllLines(log)).containsExactly("rm");
    }

    @Test
    void podman_zero_timeout_removal_observes_stopping_before_bounded_not_found_readback()
            throws Exception {
        Path state = temp.resolve("stopping-container");
        Path inspectCount = temp.resolve("stopping-inspect-count");
        Path log = temp.resolve("stopping-removal-log");
        Files.writeString(state, "present");
        Path engine = fakeEngine("stopping-engine", """
                #!/bin/sh
                if [ "$1" = "rm" ]; then
                  echo "$*" >> "$FAKE_ENGINE_LOG"
                  if [ "$2" = "--force" ] && [ "$3" = "--time=0" ] && [ "$4" = "phase17" ]; then
                    exit 0
                  fi
                  exit 125
                fi
                if [ "$1" = "container" ] && [ "$2" = "inspect" ]; then
                  count=0
                  if [ -f "$FAKE_ENGINE_INSPECT_COUNT" ]; then
                    count=$(sed -n '1p' "$FAKE_ENGINE_INSPECT_COUNT")
                  fi
                  count=$((count + 1))
                  echo "$count" > "$FAKE_ENGINE_INSPECT_COUNT"
                  if [ "$count" -eq 1 ] && [ -f "$FAKE_ENGINE_STATE" ]; then
                    echo stopping
                    exit 0
                  fi
                  rm -f "$FAKE_ENGINE_STATE"
                  echo 'Error: no container with name or ID "phase17" found: no such container' >&2
                  exit 125
                fi
                exit 125
                """);
        ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, engine, "unused:test",
                Map.of("PATH", "/usr/bin:/bin", "FAKE_ENGINE_STATE", state.toString(),
                        "FAKE_ENGINE_INSPECT_COUNT", inspectCount.toString(),
                        "FAKE_ENGINE_LOG", log.toString()));

        ContainerSandboxProcessLauncher.ContainerRemovalObservation removal =
                ContainerSandboxProcessLauncher.removeContainer(
                        configuration, "phase17", Duration.ofSeconds(1), Duration.ofMillis(5));

        assertThat(removal.completed()).isTrue();
        assertThat(removal.removalCommandSucceeded()).isTrue();
        assertThat(removal.attempts()).isEqualTo(2);
        assertThat(removal.lastStatus()).isEqualTo("removed");
        assertThat(Files.readString(inspectCount).trim()).isEqualTo("2");
        assertThat(Files.readAllLines(log))
                .containsExactly("rm --force --time=0 phase17");
        assertThat(state).doesNotExist();
    }

    @Test
    void slow_removal_cannot_starve_reserved_final_inspection_or_escape_total_bound()
            throws Exception {
        Path log = temp.resolve("slow-removal-log");
        Path engine = fakeEngine("slow-removal-engine", """
                #!/bin/sh
                if [ "$1" = "rm" ]; then
                  echo rm >> "$FAKE_ENGINE_LOG"
                  sleep 5
                  exit 0
                fi
                if [ "$1" = "container" ] && [ "$2" = "inspect" ]; then
                  echo inspect >> "$FAKE_ENGINE_LOG"
                  sleep 0.075
                  echo 'Error: no container with name or ID "phase17" found: no such container' >&2
                  exit 125
                fi
                exit 125
                """);
        ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, engine, "unused:test",
                Map.of("PATH", "/usr/bin:/bin", "FAKE_ENGINE_LOG", log.toString()));

        long started = System.nanoTime();
        ContainerSandboxProcessLauncher.ContainerRemovalObservation removal =
                ContainerSandboxProcessLauncher.removeContainer(
                        configuration, "phase17", Duration.ofMillis(800), Duration.ofMillis(5));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(removal.completed()).isTrue();
        assertThat(removal.removalCommandSucceeded()).isFalse();
        assertThat(removal.attempts()).isEqualTo(1);
        assertThat(removal.lastStatus()).isEqualTo("removed");
        assertThat(Files.readAllLines(log)).containsExactly("rm", "inspect");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void inspect_engine_error_is_not_misclassified_as_container_removal() throws Exception {
        Path engine = fakeEngine("failing-engine", """
                #!/bin/sh
                if [ "$1" = "container" ] && [ "$2" = "inspect" ]; then
                  echo 'Error: container engine socket not found' >&2
                  exit 125
                fi
                exit 1
                """);
        ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, engine, "unused:test",
                Map.of("PATH", "/usr/bin:/bin"));

        ContainerSandboxProcessLauncher.ContainerRemovalObservation removal =
                ContainerSandboxProcessLauncher.removeContainer(
                        configuration, "phase17", Duration.ofMillis(400), Duration.ofMillis(5));

        assertThat(removal.completed()).isFalse();
        assertThat(removal.attempts()).isGreaterThan(1);
        assertThat(removal.lastStatus()).isEqualTo("unknown");
        assertThat(removal.failureMessage()).contains("could not be verified");
    }

    @Test
    void primary_failure_and_typed_cleanup_failure_are_both_preserved() {
        SandboxFailure timeout = SandboxFailure.of(
                SandboxFailureCode.PROCESS_TIMEOUT, "timed out", Set.of());
        SandboxCleanupObservation cleanup = SandboxCleanupObservation.failed(
                1, List.of(123L), "container remains after bounded retries");

        Optional<SandboxFailure> resultFailure =
                ContainerSandboxProcessLauncher.selectResultFailure(timeout, cleanup);

        assertThat(resultFailure).contains(timeout);
        assertThat(cleanup.completed()).isFalse();
        assertThat(cleanup.failure()).isPresent();
        assertThat(cleanup.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.SANDBOX_CLEANUP_FAILED);
        assertThat(cleanup.survivors()).containsExactly(123L);
        assertThat(ContainerSandboxProcessLauncher.selectResultFailure(null, cleanup))
                .contains(cleanup.failure().orElseThrow());
    }

    @Test
    void removed_container_and_reaped_client_do_not_classify_detached_engine_helper_as_workload() {
        long enginePid = 101L;
        long detachedHelperPid = 202L;
        SandboxCleanupObservation localCleanup = SandboxCleanupObservation.failed(
                1, List.of(detachedHelperPid), "local descendant remains alive");

        SandboxCleanupObservation cleanup = ContainerSandboxProcessLauncher.cleanupObservation(
                "phase17", enginePid,
                new ContainerSandboxProcessLauncher.ContainerRemovalObservation(
                        true, true, 2, "removed", ""),
                localCleanup, true);

        assertThat(cleanup.completed()).isTrue();
        assertThat(cleanup.namedContainerRemoved()).isTrue();
        assertThat(cleanup.engineClientReaped()).isTrue();
        assertThat(cleanup.workloadProcessesContained()).isTrue();
        assertThat(cleanup.captureStreamsClosed()).isTrue();
        assertThat(cleanup.survivors()).isEmpty();
        assertThat(cleanup.detachedEngineHelpers()).containsExactly(detachedHelperPid);
        assertThat(cleanup.failure()).isEmpty();
    }

    @Test
    void live_engine_client_remains_a_blocking_cleanup_survivor_after_container_removal() {
        long enginePid = 303L;
        long detachedHelperPid = 404L;
        SandboxCleanupObservation localCleanup = SandboxCleanupObservation.failed(
                2, List.of(enginePid, detachedHelperPid), "local processes remain alive");

        SandboxCleanupObservation cleanup = ContainerSandboxProcessLauncher.cleanupObservation(
                "phase17", enginePid,
                new ContainerSandboxProcessLauncher.ContainerRemovalObservation(
                        true, true, 1, "removed", ""),
                localCleanup, true);

        assertThat(cleanup.completed()).isFalse();
        assertThat(cleanup.namedContainerRemoved()).isTrue();
        assertThat(cleanup.engineClientReaped()).isFalse();
        assertThat(cleanup.workloadProcessesContained()).isTrue();
        assertThat(cleanup.survivors()).containsExactly(enginePid);
        assertThat(cleanup.detachedEngineHelpers()).containsExactly(detachedHelperPid);
        assertThat(cleanup.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.SANDBOX_CLEANUP_FAILED);
        assertThat(cleanup.failureMessage()).contains("engine client remains alive");
    }

    @Test
    void unverified_container_absence_keeps_possible_workload_descendants_as_survivors() {
        long enginePid = 505L;
        long possibleWorkloadPid = 606L;
        SandboxCleanupObservation localCleanup = SandboxCleanupObservation.failed(
                1, List.of(possibleWorkloadPid), "local descendant remains alive");

        SandboxCleanupObservation cleanup = ContainerSandboxProcessLauncher.cleanupObservation(
                "phase17", enginePid,
                new ContainerSandboxProcessLauncher.ContainerRemovalObservation(
                        false, true, 4, "running", "container remains"),
                localCleanup, true);

        assertThat(cleanup.completed()).isFalse();
        assertThat(cleanup.namedContainerRemoved()).isFalse();
        assertThat(cleanup.engineClientReaped()).isTrue();
        assertThat(cleanup.workloadProcessesContained()).isFalse();
        assertThat(cleanup.survivors()).containsExactly(possibleWorkloadPid);
        assertThat(cleanup.detachedEngineHelpers()).isEmpty();
        assertThat(cleanup.failureMessage())
                .contains("container remains", "workload containment is not proven");
    }

    @Test
    void open_capture_streams_prevent_completed_cleanup_even_with_container_and_client_gone() {
        SandboxCleanupObservation cleanup = ContainerSandboxProcessLauncher.cleanupObservation(
                "phase17", 707L,
                new ContainerSandboxProcessLauncher.ContainerRemovalObservation(
                        true, true, 1, "removed", ""),
                SandboxCleanupObservation.succeeded(0, List.of()), false);

        assertThat(cleanup.completed()).isFalse();
        assertThat(cleanup.namedContainerRemoved()).isTrue();
        assertThat(cleanup.engineClientReaped()).isTrue();
        assertThat(cleanup.workloadProcessesContained()).isTrue();
        assertThat(cleanup.captureStreamsClosed()).isFalse();
        assertThat(cleanup.failureMessage()).contains("capture streams remain open");
    }

    @Test
    void cleanup_diagnostic_contains_typed_failure_and_runtime_identity() {
        SandboxCleanupObservation cleanup = ContainerSandboxProcessLauncher.cleanupObservation(
                "phase17-diagnostic", 808L,
                new ContainerSandboxProcessLauncher.ContainerRemovalObservation(
                        false, false, 3, "stopping", "container remains"),
                SandboxCleanupObservation.failed(
                        1, List.of(808L), "engine client remains"), false);

        assertThat(cleanup.diagnostic()).contains(
                "failureCode=SANDBOX_CLEANUP_FAILED",
                "failureMessage=container remains",
                "survivors=[808]",
                "enginePid=808",
                "containerName=phase17-diagnostic",
                "containerStatus=stopping");
    }

    @Test
    void launcher_timeout_remains_primary_when_named_container_cleanup_succeeds() throws Exception {
        Path state = temp.resolve("live-container");
        Path runPid = temp.resolve("run-pid");
        Path engine = fakeEngine("launch-engine", """
                #!/bin/sh
                if [ "$1" = "run" ]; then
                  touch "$FAKE_ENGINE_STATE"
                  echo "$$" > "$FAKE_ENGINE_RUN_PID"
                  exec /bin/sleep 30
                fi
                if [ "$1" = "rm" ]; then
                  if [ -f "$FAKE_ENGINE_STATE" ]; then
                    kill "$(sed -n '1p' "$FAKE_ENGINE_RUN_PID")" 2>/dev/null || true
                    rm -f "$FAKE_ENGINE_STATE"
                    exit 0
                  fi
                  echo 'Error: no such container' >&2
                  exit 125
                fi
                if [ "$1" = "container" ] && [ "$2" = "inspect" ]; then
                  if [ -f "$FAKE_ENGINE_STATE" ]; then
                    echo running
                    exit 0
                  fi
                  echo 'Error: no container with name or ID "phase17" found: no such container' >&2
                  exit 125
                fi
                exit 125
                """);
        SandboxRuntimeCapabilities capabilities = SandboxRuntimeCapabilities.available(Set.of());
        ContainerEngineConfiguration configuration = new ContainerEngineConfiguration(
                ContainerEngineConfiguration.Kind.PODMAN, engine, "unused:test",
                Map.of("PATH", "/usr/bin:/bin", "FAKE_ENGINE_STATE", state.toString(),
                        "FAKE_ENGINE_RUN_PID", runPid.toString()));
        ContainerSandboxProcessLauncher launcher = new ContainerSandboxProcessLauncher(
                configuration, capabilities, Optional.empty());
        Path workspace = Files.createDirectory(temp.resolve("timeout-workspace"));
        Path input = Files.writeString(temp.resolve("timeout-input"), "input");
        Files.createDirectories(workspace.resolve("tmp"));
        Files.createDirectories(workspace.resolve("out"));
        SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                ProcessRequirement.of(Set.of("/bin/sleep"), "/bin/sleep", List.of("30"),
                        Duration.ofMillis(100)),
                FilesystemPolicy.exact(Set.of(input), workspace, workspace.resolve("tmp"),
                        workspace.resolve("out"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of()), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(1024),
                DeviceExposurePolicy.none());

        SandboxExecutionResult result = launcher.launch(
                EffectiveSandboxExecutionSpecification.resolved(requirement, capabilities),
                SandboxCancellation.never());

        assertThat(result.failure()).isPresent();
        assertThat(result.failure().orElseThrow().code())
                .isEqualTo(SandboxFailureCode.PROCESS_TIMEOUT);
        assertThat(result.observation().cleanup().completed()).isTrue();
        assertThat(result.observation().cleanup().failure()).isEmpty();
        assertThat(state).doesNotExist();
        assertThat(ProcessHandle.of(result.observation().handle().processId())
                .map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    private Path fakeEngine(String name, String script) throws Exception {
        Path engine = temp.resolve(name);
        Files.writeString(engine, script);
        Files.setPosixFilePermissions(engine, PosixFilePermissions.fromString("rwx------"));
        return engine;
    }
}
