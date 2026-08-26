package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Detects bwrap only by executing the same mount/namespace shape used in production. */
@org.springframework.modulith.NamedInterface("API")
public final class BubblewrapSandboxCapabilityDetector {
    private static final Path BWRAP = Path.of("/usr/bin/bwrap");

    private BubblewrapSandboxCapabilityDetector() {}

    public static BubblewrapSandboxDetection detect() {
        if (!Files.isRegularFile(BWRAP) || !Files.isExecutable(BWRAP)) {
            return new BubblewrapSandboxDetection(
                    false, Optional.empty(), "/usr/bin/bwrap is not installed");
        }
        ProbeObservation probe = productionShapeProbe();
        if (!probe.succeeded()) {
            return new BubblewrapSandboxDetection(true, Optional.empty(), probe.diagnostic());
        }
        Set<SandboxCapability> capabilities = EnumSet.of(
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
        SandboxRuntimeCapabilities evidence = SandboxRuntimeCapabilities.detected(
                capabilities, "bubblewrap-production-shape", Instant.now());
        return new BubblewrapSandboxDetection(true,
                Optional.of(new BubblewrapSandboxProcessLauncher(BWRAP, evidence)),
                "bubblewrap production-shape enforcement probe passed");
    }

    private static ProbeObservation productionShapeProbe() {
        Path probeRoot = null;
        try {
            probeRoot = Files.createTempDirectory("media-platform-bwrap-probe-");
            Path workspace = Files.createDirectory(probeRoot.resolve("workspace"));
            Path temporary = Files.createDirectory(workspace.resolve(".sandbox-tmp"));
            Path output = Files.createDirectory(workspace.resolve(".sandbox-output"));
            Path input = Files.writeString(workspace.resolve("input"), "probe");
            ProcessRequirement process = ProcessRequirement.of(
                    Set.of("/usr/bin/env"), "/usr/bin/env", List.of(), Duration.ofSeconds(5));
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    process,
                    FilesystemPolicy.exact(Set.of(input), workspace, temporary, output, workspace),
                    NetworkPolicy.none(),
                    EnvironmentPolicy.exact(Map.of("PATH", "/usr/bin:/bin", "LANG", "C")),
                    SecretExposure.none(), PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(4096), DeviceExposurePolicy.none());
            EffectiveSandboxExecutionSpecification specification =
                    EffectiveSandboxExecutionSpecification.resolved(requirement,
                            SandboxRuntimeCapabilities.unavailable("bubblewrap-probe"));
            List<String> command = BubblewrapSandboxProcessLauncher.buildCommand(
                    BWRAP, specification);
            BubblewrapProcess.ProbeResult result = BubblewrapProcess.execute(
                    command, Duration.ofSeconds(5));
            Set<String> environment = result.stdout().lines()
                    .filter(line -> !line.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> expected = Set.of(
                    "PATH=/usr/bin:/bin", "LANG=C", "PWD=/workspace");
            if (!result.succeeded() || !environment.equals(expected)) {
                String diagnostic = result.succeeded()
                        ? "bubblewrap probe leaked or omitted environment entries"
                        : "bubblewrap production-shape probe failed: " + result.diagnostic();
                return new ProbeObservation(false, diagnostic);
            }
            return new ProbeObservation(true, "");
        } catch (IOException | RuntimeException failure) {
            return new ProbeObservation(false,
                    "bubblewrap production-shape probe failed: "
                            + failure.getClass().getSimpleName());
        } finally {
            deleteProbeTree(probeRoot);
        }
    }

    private static void deleteProbeTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record ProbeObservation(boolean succeeded, String diagnostic) {}
}
