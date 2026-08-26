package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BubblewrapSandboxProcessLauncherIntegrationTest {
    private static final Path BWRAP = Path.of("/usr/bin/bwrap");

    @TempDir Path temp;

    @Test
    void real_bubblewrap_enforces_the_advertised_host_binary_boundaries() throws Exception {
        assumeTrue(Files.isRegularFile(BWRAP) && Files.isExecutable(BWRAP),
                "/usr/bin/bwrap is not installed");

        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        assertThat(detection.launcher()).as(detection.diagnostic()).isPresent();
        BubblewrapSandboxProcessLauncher launcher = detection.launcher().orElseThrow();

        assertThat(launcher.capabilities().capabilities()).containsExactlyInAnyOrder(
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

        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path input = Files.writeString(workspace.resolve("immutable-input.txt"), "immutable");
        Path hostSentinel = Files.writeString(temp.resolve("host-only-sentinel"), "host-only");

        SandboxExecutionResult environment = launch(launcher, workspace, input,
                command("/usr/bin/env", List.of()),
                Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "ONLY_ALLOWED", "visible"),
                SandboxCancellation.never());
        assertSuccess(environment);
        assertThat(environment.stdout().utf8().lines()).containsExactlyInAnyOrder(
                "PATH=/usr/bin:/bin", "LANG=C", "ONLY_ALLOWED=visible", "PWD=/workspace");
        assertThat(environment.stdout().utf8()).doesNotContain(
                "AWS_SECRET_ACCESS_KEY", "phase17-ambient-aws-secret",
                "HOME", "/phase17-ambient-home-not-mounted");

        SandboxExecutionResult network = launch(launcher, workspace, input,
                command("/usr/bin/ip", List.of("-o", "link")), Map.of(),
                SandboxCancellation.never());
        assertSuccess(network);
        assertThat(network.stdout().utf8()).contains("lo:").doesNotContain("eth0", "wlan0");
        SandboxExecutionResult routes = launch(launcher, workspace, input,
                command("/usr/bin/ip", List.of("route")), Map.of(),
                SandboxCancellation.never());
        assertSuccess(routes);
        assertThat(routes.stdout().utf8()).isBlank();

        SandboxExecutionResult processes = launch(launcher, workspace, input,
                command("/usr/bin/ps", List.of("-e", "-o", "comm=")), Map.of(),
                SandboxCancellation.never());
        assertSuccess(processes);
        assertThat(processes.stdout().utf8()).contains("ps").doesNotContain("java", "gradle");

        SandboxExecutionResult identity = launch(launcher, workspace, input,
                command("/usr/bin/id", List.of("-u")), Map.of(),
                SandboxCancellation.never());
        assertSuccess(identity);
        assertThat(identity.stdout().utf8().trim()).isNotEqualTo("0");

        SandboxExecutionResult devices = launch(launcher, workspace, input,
                command("/usr/bin/find", List.of("/dev", "-mindepth", "1", "-maxdepth", "1",
                        "-printf", "%f\\n")), Map.of(), SandboxCancellation.never());
        assertSuccess(devices);
        assertThat(devices.stdout().utf8())
                .doesNotContain("dri", "video", "snd", "nvidia", "docker.sock");

        SandboxExecutionResult readableInput = launch(launcher, workspace, input,
                command("/usr/bin/cat", List.of(input.toString())), Map.of(),
                SandboxCancellation.never());
        assertSuccess(readableInput);
        assertThat(readableInput.stdout().utf8()).isEqualTo("immutable");

        assertFailure(launch(launcher, workspace, input,
                command("/usr/bin/touch", List.of(input.toString())), Map.of(),
                SandboxCancellation.never()), SandboxFailureCode.PROCESS_CRASHED);
        assertFailure(launch(launcher, workspace, input,
                command("/usr/bin/cat", List.of(hostSentinel.toString())), Map.of(),
                SandboxCancellation.never()), SandboxFailureCode.PROCESS_CRASHED);
        assertFailure(launch(launcher, workspace, input,
                command("/usr/bin/touch", List.of("/workspace/not-authorized")), Map.of(),
                SandboxCancellation.never()), SandboxFailureCode.PROCESS_CRASHED);
        assertFailure(launch(launcher, workspace, input,
                command("/usr/bin/touch", List.of("/sandbox-inputs/not-authorized")), Map.of(),
                SandboxCancellation.never()), SandboxFailureCode.PROCESS_CRASHED);

        SandboxExecutionResult writableTemporary = launch(launcher, workspace, input,
                command("/usr/bin/touch", List.of("/workspace/.sandbox-tmp/temporary-proof")),
                Map.of(), SandboxCancellation.never());
        assertSuccess(writableTemporary);
        assertThat(workspace.resolve(".sandbox-tmp/temporary-proof")).exists();

        SandboxExecutionResult writableOutput = launch(launcher, workspace, input,
                command("/usr/bin/touch", List.of("/workspace/.sandbox-output/output-proof")),
                Map.of(), SandboxCancellation.never());
        assertSuccess(writableOutput);
        assertThat(workspace.resolve(".sandbox-output/output-proof")).exists();

        SandboxExecutionResult bounded = launch(launcher, workspace, input,
                command("/usr/bin/seq", List.of("1", "10000")), Map.of(),
                SandboxCancellation.never(), Duration.ofSeconds(5), 64);
        assertSuccess(bounded);
        assertThat(bounded.stdout().truncated()).isTrue();
        assertThat(bounded.stdout().bytes()).hasSizeLessThanOrEqualTo(64);

        SandboxExecutionResult timeout = launch(launcher, workspace, input,
                command("/usr/bin/timeout", List.of("30", "/usr/bin/sleep", "30")), Map.of(),
                SandboxCancellation.never(), Duration.ofMillis(250), 1024);
        assertFailure(timeout, SandboxFailureCode.PROCESS_TIMEOUT);
        assertCleanupCompleteAndReaped(timeout);

        AtomicBoolean cancelled = new AtomicBoolean();
        Thread.ofPlatform().start(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            cancelled.set(true);
        });
        SandboxExecutionResult cancellation = launch(launcher, workspace, input,
                command("/usr/bin/timeout", List.of("30", "/usr/bin/sleep", "30")), Map.of(),
                cancelled::get, Duration.ofSeconds(5), 1024);
        assertFailure(cancellation, SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT);
        assertCleanupCompleteAndReaped(cancellation);

        SandboxExecutionResult throughNeutralBoundary = LocalSandboxProcess.execute(
                List.of("/usr/bin/cat", input.toString()), workspace, workspace, Set.of(input),
                Map.of(), Duration.ofSeconds(5), 1024, SandboxCancellation.never());
        assertSuccess(throughNeutralBoundary);
        assertThat(throughNeutralBoundary.stdout().utf8()).isEqualTo("immutable");
    }

    private SandboxExecutionResult launch(
            BubblewrapSandboxProcessLauncher launcher,
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            SandboxCancellation cancellation) throws Exception {
        return launch(launcher, workspace, input, process, environment, cancellation,
                process.timeout(), 4096);
    }

    private SandboxExecutionResult launch(
            BubblewrapSandboxProcessLauncher launcher,
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            SandboxCancellation cancellation,
            Duration timeout,
            long captureBytes) throws Exception {
        ProcessRequirement timed = ProcessRequirement.of(
                process.allowedExecutables(), process.executable(), process.arguments(), timeout);
        return launcher.launchResolved(requirement(workspace, input, timed, environment, captureBytes),
                cancellation);
    }

    private SandboxExecutionRequirement requirement(
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            long captureBytes) {
        return new SandboxExecutionRequirement(process,
                FilesystemPolicy.exact(Set.of(input), workspace,
                        workspace.resolve(".sandbox-tmp"),
                        workspace.resolve(".sandbox-output"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(environment), SecretExposure.none(),
                PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(captureBytes),
                DeviceExposurePolicy.none());
    }

    private ProcessRequirement command(String executable, List<String> arguments) {
        return ProcessRequirement.of(Set.of(executable), executable, arguments, Duration.ofSeconds(5));
    }

    private void assertSuccess(SandboxExecutionResult result) {
        assertThat(result.failure()).as(result.toString()).isEmpty();
        assertThat(result.exitCode()).hasValue(0);
    }

    private void assertFailure(SandboxExecutionResult result, SandboxFailureCode code) {
        assertThat(result.failure()).as(result.toString()).isPresent();
        assertThat(result.failure().orElseThrow().code()).isEqualTo(code);
    }

    private void assertCleanupCompleteAndReaped(SandboxExecutionResult result) {
        SandboxCleanupObservation cleanup = result.observation().cleanup();
        assertThat(cleanup.completed()).as(cleanup.diagnostic()).isTrue();
        assertThat(cleanup.failure()).as(cleanup.diagnostic()).isEmpty();
        assertThat(cleanup.survivors()).isEmpty();
        assertThat(ProcessHandle.of(result.observation().handle().processId())
                .map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }
}
