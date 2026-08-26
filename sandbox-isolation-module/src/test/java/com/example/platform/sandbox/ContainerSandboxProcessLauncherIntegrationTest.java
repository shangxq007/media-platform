package com.example.platform.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
class ContainerSandboxProcessLauncherIntegrationTest {
    private static final String IMAGE = "docker.io/library/alpine:3.20";

    @TempDir Path temp;

    @Test
    void rootless_container_mechanically_enforces_the_advertised_boundaries() throws Exception {
        AtomicReference<ScopedSecretValue> resolvedSecret = new AtomicReference<>();
        SandboxSecretResolver secrets = reference -> {
            ScopedSecretValue value = ScopedSecretValue.resolved(
                    reference, "SCOPED_TOKEN", "phase17-runtime-secret".toCharArray());
            resolvedSecret.set(value);
            return value;
        };
        ContainerSandboxDetection detection = ContainerSandboxCapabilityDetector.detect(
                ContainerEnginePreference.AUTO, IMAGE, Optional.of(secrets));
        assumeTrue(detection.supportedEngineInstalled(), "Podman or Docker is not installed");
        assumeTrue(detection.launcher().isPresent()
                        || !onlyRootfulEnginesUnavailable(detection.diagnostic()),
                "Rootless container engine unavailable: " + detection.diagnostic());
        assertThat(detection.launcher()).as(detection.diagnostic()).isPresent();
        ContainerSandboxProcessLauncher launcher = detection.launcher().orElseThrow();

        assertThat(launcher.capabilities().capabilities()).contains(
                SandboxCapability.PROCESS_TREE_CONTAINMENT,
                SandboxCapability.FILESYSTEM_ACCESS_ISOLATION,
                SandboxCapability.NETWORK_NONE,
                SandboxCapability.ENVIRONMENT_CLEARING,
                SandboxCapability.SECRET_INJECTION,
                SandboxCapability.TEMPORARY_STORAGE_LIMIT,
                SandboxCapability.UNPRIVILEGED_EXECUTION,
                SandboxCapability.HOST_EXPOSURE_DENIAL,
                SandboxCapability.DEVICE_NONE);
        assertThat(launcher.capabilities().capabilities())
                .doesNotContain(SandboxCapability.OUTPUT_STORAGE_LIMIT);

        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path input = Files.writeString(temp.resolve("immutable-input.txt"), "immutable");
        Path hostSentinel = Files.writeString(temp.resolve("host-only-sentinel"), "host-only");

        SandboxExecutionResult environment = launch(launcher, workspace, input,
                command("/usr/bin/env", List.of()), Map.of("ONLY_ALLOWED", "visible"),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never());
        assertSuccess(environment);
        assertThat(java.lang.System.getenv("HOME"))
                .isEqualTo("/phase17-ambient-home-not-mounted");
        assertThat(java.lang.System.getenv("AWS_SECRET_ACCESS_KEY"))
                .isEqualTo("phase17-ambient-aws-secret");
        assertThat(environment.stdout().utf8())
                .contains("ONLY_ALLOWED=visible", "HOME=/sandbox/workspace")
                .doesNotContain("AWS_SECRET_ACCESS_KEY", "phase17-ambient-aws-secret",
                        "/phase17-ambient-home-not-mounted");

        SandboxExecutionResult network = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("ip", "link")), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never());
        assertSuccess(network);
        assertThat(network.stdout().utf8()).contains("lo:").doesNotContain("eth0");

        assertFailure(launch(launcher, workspace, input,
                command("/bin/busybox", List.of("touch", "/read-only-root-proof")), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never()),
                SandboxFailureCode.PROCESS_CRASHED);
        assertFailure(launch(launcher, workspace, input,
                command("/bin/busybox", List.of("cat", hostSentinel.toString())), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never()),
                SandboxFailureCode.PROCESS_CRASHED);
        assertFailure(launch(launcher, workspace, input,
                command("/bin/busybox", List.of("touch", "/sandbox/inputs/input-0")), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never()),
                SandboxFailureCode.PROCESS_CRASHED);

        SandboxExecutionResult writableOutput = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("touch", "/sandbox/workspace/out/proof")), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never());
        assertSuccess(writableOutput);
        assertThat(workspace.resolve("out/proof")).exists();

        SandboxExecutionResult writableTemporary = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("touch", "/sandbox/workspace/tmp/ephemeral")), Map.of(),
                SecretExposure.none(), resources(launcher, 4096), SandboxCancellation.never());
        assertSuccess(writableTemporary);
        assertThat(workspace.resolve("tmp/ephemeral")).doesNotExist();

        SandboxExecutionResult posture = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("cat", "/proc/self/status")), Map.of(),
                SecretExposure.none(), resources(launcher, 8192), SandboxCancellation.never());
        assertSuccess(posture);
        assertThat(posture.stdout().utf8()).containsPattern("(?m)^NoNewPrivs:\\s+1$")
                .containsPattern("(?m)^CapEff:\\s+0+$");
        SandboxExecutionResult identity = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("id", "-u")), Map.of(),
                SecretExposure.none(), resources(launcher, 1024), SandboxCancellation.never());
        assertSuccess(identity);
        assertThat(identity.stdout().utf8().trim()).isNotEqualTo("0");

        SandboxExecutionRequirement secretRequirement = requirement(workspace, input,
                command("/bin/busybox", List.of("printenv", "SCOPED_TOKEN")), Map.of(),
                new SecretExposure(Set.of(OpaqueSecretReference.of("runtime-token"))),
                resources(launcher, 4096));
        assertThat(secretRequirement.toString()).doesNotContain("phase17-runtime-secret");
        SandboxExecutionResult secretResult = launcher.launchResolved(
                secretRequirement, SandboxCancellation.never());
        assertSuccess(secretResult);
        assertThat(secretResult.stdout().utf8()).contains("[REDACTED]")
                .doesNotContain("phase17-runtime-secret");
        assertThat(secretResult.toString()).doesNotContain("phase17-runtime-secret");
        assertThat(resolvedSecret.get().toString()).doesNotContain("phase17-runtime-secret");
        assertThat(resolvedSecret.get().copyValue()).containsOnly('\0');

        ContainerSandboxDetection failingSecretDetection = ContainerSandboxCapabilityDetector.detect(
                ContainerEnginePreference.AUTO, IMAGE,
                Optional.of(reference -> { throw new SandboxSecretResolutionException("unavailable"); }));
        assertThat(failingSecretDetection.launcher()).as(failingSecretDetection.diagnostic()).isPresent();
        SandboxExecutionResult resolutionFailure = failingSecretDetection.launcher().orElseThrow()
                .launchResolved(secretRequirement, SandboxCancellation.never());
        assertFailure(resolutionFailure, SandboxFailureCode.SECRET_INJECTION_FAILED);

        SandboxExecutionResult bounded = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("seq", "1", "10000")), Map.of(),
                SecretExposure.none(), resources(launcher, 64), SandboxCancellation.never());
        assertSuccess(bounded);
        assertThat(bounded.stdout().truncated()).isTrue();
        assertThat(bounded.stdout().bytes()).hasSizeLessThanOrEqualTo(64);

        SandboxExecutionResult timeout = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("sleep", "30")), Map.of(),
                SecretExposure.none(), resources(launcher, 1024), SandboxCancellation.never(),
                Duration.ofMillis(250));
        assertFailure(timeout, SandboxFailureCode.PROCESS_TIMEOUT);
        assertCleanupComplete(timeout);
        assertThat(ProcessHandle.of(timeout.observation().handle().processId())
                .map(ProcessHandle::isAlive).orElse(false)).isFalse();
        assertThat(launcher.hasRunningSandboxContainers()).isFalse();

        AtomicBoolean cancelled = new AtomicBoolean();
        Thread.ofPlatform().start(() -> {
            try { Thread.sleep(150); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            cancelled.set(true);
        });
        SandboxExecutionResult cancellation = launch(launcher, workspace, input,
                command("/bin/busybox", List.of("sleep", "30")), Map.of(),
                SecretExposure.none(), resources(launcher, 1024), cancelled::get,
                Duration.ofSeconds(5));
        assertFailure(cancellation, SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT);
        assertCleanupComplete(cancellation);
        assertThat(ProcessHandle.of(cancellation.observation().handle().processId())
                .map(ProcessHandle::isAlive).orElse(false)).isFalse();
        assertThat(launcher.hasRunningSandboxContainers()).isFalse();
    }

    @Test
    void rootful_engine_unavailability_diagnostic_classification_is_exact() {
        assertThat(onlyRootfulEnginesUnavailable("docker: engine is not rootless")).isTrue();
        assertThat(onlyRootfulEnginesUnavailable(
                "podman: engine is not rootless; docker: engine is not rootless")).isTrue();
        assertThat(onlyRootfulEnginesUnavailable(
                "docker: hardening probe failed: runtime setup failed")).isFalse();
        assertThat(onlyRootfulEnginesUnavailable(
                "docker: engine is not rootless; podman: hardening probe failed: resource limit"))
                .isFalse();
        assertThat(onlyRootfulEnginesUnavailable(
                "/usr/bin/docker: executable version behavior is not Podman or Docker")).isFalse();
        assertThat(onlyRootfulEnginesUnavailable(
                "docker: rootless info probe failed: runtime setup failed")).isFalse();
    }

    private static boolean onlyRootfulEnginesUnavailable(String diagnostic) {
        return List.of(diagnostic.split("; ", -1)).stream()
                .allMatch(entry -> entry.equals("podman: engine is not rootless")
                        || entry.equals("docker: engine is not rootless"));
    }

    private SandboxExecutionResult launch(
            ContainerSandboxProcessLauncher launcher,
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            SecretExposure secrets,
            ResourceEnforcementLimits resources,
            SandboxCancellation cancellation) throws Exception {
        return launch(launcher, workspace, input, process, environment, secrets, resources,
                cancellation, process.timeout());
    }

    private SandboxExecutionResult launch(
            ContainerSandboxProcessLauncher launcher,
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            SecretExposure secrets,
            ResourceEnforcementLimits resources,
            SandboxCancellation cancellation,
            Duration timeout) throws Exception {
        ProcessRequirement timed = ProcessRequirement.of(
                process.allowedExecutables(), process.executable(), process.arguments(), timeout);
        return launcher.launchResolved(
                requirement(workspace, input, timed, environment, secrets, resources), cancellation);
    }

    private SandboxExecutionRequirement requirement(
            Path workspace,
            Path input,
            ProcessRequirement process,
            Map<String, String> environment,
            SecretExposure secrets,
            ResourceEnforcementLimits resources) throws Exception {
        Files.createDirectories(workspace.resolve("tmp"));
        Files.createDirectories(workspace.resolve("out"));
        return new SandboxExecutionRequirement(process,
                FilesystemPolicy.exact(Set.of(input), workspace, workspace.resolve("tmp"),
                        workspace.resolve("out"), workspace),
                NetworkPolicy.none(), EnvironmentPolicy.exact(environment), secrets,
                PrivilegePolicy.unprivileged(), resources, DeviceExposurePolicy.none());
    }

    private ProcessRequirement command(String executable, List<String> arguments) {
        return ProcessRequirement.of(Set.of(executable), executable, arguments, Duration.ofSeconds(5));
    }

    private ResourceEnforcementLimits resources(
            ContainerSandboxProcessLauncher launcher, long captureBytes) {
        SandboxRuntimeCapabilities capabilities = launcher.capabilities();
        return new ResourceEnforcementLimits(
                capabilities.supports(SandboxCapability.CPU_COUNT_LIMIT)
                        ? Optional.of(1.0) : Optional.empty(),
                capabilities.supports(SandboxCapability.MEMORY_LIMIT)
                        ? OptionalLong.of(64L << 20) : OptionalLong.empty(),
                capabilities.supports(SandboxCapability.PROCESS_COUNT_LIMIT)
                        ? OptionalInt.of(32) : OptionalInt.empty(),
                capabilities.supports(SandboxCapability.OPEN_FILE_LIMIT)
                        ? OptionalInt.of(64) : OptionalInt.empty(),
                OptionalLong.of(16L << 20), OptionalLong.empty(), captureBytes);
    }

    private void assertSuccess(SandboxExecutionResult result) {
        assertThat(result.failure()).as(result.toString()).isEmpty();
        assertThat(result.exitCode()).hasValue(0);
    }

    private void assertFailure(SandboxExecutionResult result, SandboxFailureCode code) {
        assertThat(result.failure()).as(result.toString()).isPresent();
        assertThat(result.failure().orElseThrow().code()).isEqualTo(code);
    }

    private void assertCleanupComplete(SandboxExecutionResult result) {
        SandboxCleanupObservation cleanup = result.observation().cleanup();
        assertThat(cleanup.completed()).as(cleanup.diagnostic()).isTrue();
        assertThat(cleanup.failure()).as(cleanup.diagnostic()).isEmpty();
    }
}
