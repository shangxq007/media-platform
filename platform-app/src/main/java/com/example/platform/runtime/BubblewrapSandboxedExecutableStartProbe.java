package com.example.platform.runtime;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.BubblewrapSandboxDetection;
import com.example.platform.sandbox.DeviceExposurePolicy;
import com.example.platform.sandbox.EnvironmentPolicy;
import com.example.platform.sandbox.FilesystemPolicy;
import com.example.platform.sandbox.NetworkPolicy;
import com.example.platform.sandbox.PrivilegePolicy;
import com.example.platform.sandbox.ProcessRequirement;
import com.example.platform.sandbox.ResourceEnforcementLimits;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SandboxExecutionResolver;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SecretExposure;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P1-1A.7: the production bounded start probe.
 *
 * <p>It composes the exact sandbox boundary the real execution uses — the same launcher resolution
 * ({@link BubblewrapSandboxCapabilityDetector#detect()}), the same root-only filesystem policy, the
 * same environment clearing and the same no-network/unprivileged/device-none policies — and runs the
 * caller-supplied executable with a bounded argv. It adds no package manager, no dynamic-linker
 * emulation and no library enumeration: it observes whether the process starts.
 */
public final class BubblewrapSandboxedExecutableStartProbe
        implements SandboxedExecutableStartProbe {

    private static final String TEMPORARY_DIRECTORY = ".sandbox-tmp";
    private static final String OUTPUT_DIRECTORY = ".sandbox-output";

    private final Path workspaceRoot;
    private final Duration timeout;
    private final long captureBytes;

    public BubblewrapSandboxedExecutableStartProbe(
            Path workspaceRoot, Duration timeout, long captureBytes) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath()
                .normalize();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.captureBytes = captureBytes;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("probe timeout must be positive");
        }
        if (captureBytes <= 0) {
            throw new IllegalArgumentException("probe capture budget must be positive");
        }
    }

    @Override
    public Result probe(Path executable, List<String> arguments) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        Path normalized = executable.toAbsolutePath().normalize();
        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        if (detection.launcher().isEmpty()) {
            return new Result(Status.UNKNOWN, "", detection.diagnostic());
        }
        try {
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(
                            Set.of(normalized.toString()),
                            normalized.toString(),
                            List.copyOf(arguments),
                            timeout),
                    FilesystemPolicy.exact(
                            Set.of(normalized),
                            workspaceRoot,
                            workspaceRoot.resolve(TEMPORARY_DIRECTORY),
                            workspaceRoot.resolve(OUTPUT_DIRECTORY),
                            workspaceRoot),
                    NetworkPolicy.none(),
                    // Exactly the environment map the production execution policy sets.
                    EnvironmentPolicy.exact(Map.of(
                            "PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C")),
                    SecretExposure.none(),
                    PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(captureBytes),
                    DeviceExposurePolicy.none());
            var launcher = detection.launcher().orElseThrow();
            SandboxResolution resolution =
                    SandboxExecutionResolver.resolve(requirement, launcher.capabilities());
            if (resolution instanceof SandboxResolution.Rejected rejected) {
                return new Result(Status.FAILED, "", rejected.failure().code().name());
            }
            SandboxExecutionResult result = launcher.launch(
                    ((SandboxResolution.Resolved) resolution).specification(),
                    SandboxCancellation.never());
            if (result.failure().isPresent()) {
                return new Result(Status.FAILED, "", result.failure().orElseThrow().code().name());
            }
            if (result.exitCode().orElse(-1) != 0) {
                return new Result(Status.FAILED, "", "exit code " + result.exitCode().orElse(-1));
            }
            return new Result(Status.STARTED, result.stdout().utf8(), "exit 0");
        } catch (IOException | RuntimeException failure) {
            return new Result(Status.UNKNOWN, "", failure.getClass().getSimpleName());
        }
    }
}
