package com.example.platform.ffmpeg;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.DeviceExposurePolicy;
import com.example.platform.sandbox.EnvironmentPolicy;
import com.example.platform.sandbox.FilesystemPolicy;
import com.example.platform.sandbox.NetworkPolicy;
import com.example.platform.sandbox.PrivilegePolicy;
import com.example.platform.sandbox.ProcessRequirement;
import com.example.platform.sandbox.ResourceEnforcementLimits;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SecretExposure;
import com.example.platform.workerfabric.domain.ProviderProbeResult;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded sandboxed {@code ffmpeg -version} probe. */
public final class FfmpegRuntimeProbe {
    private static final Pattern VERSION = Pattern.compile("^ffmpeg version ([^ ]+).*$");

    private FfmpegRuntimeProbe() {}

    public static FfmpegRuntimeProbeResult probe(
            Path executable, FfmpegSandboxWorkspace workspace, Duration timeout) {
        var detection = BubblewrapSandboxCapabilityDetector.detect();
        if (detection.launcher().isEmpty()) {
            return failed(ProviderProbeResult.Status.UNKNOWN);
        }
        try {
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(Set.of(executable.toString()), executable.toString(),
                            List.of("-version"), timeout),
                    FilesystemPolicy.exact(Set.of(executable), workspace.root(),
                            workspace.temporaryRoot(), workspace.outputStagingRoot(),
                            workspace.workingDirectory()),
                    NetworkPolicy.none(),
                    EnvironmentPolicy.exact(Map.of("LANG", "C", "LC_ALL", "C")),
                    SecretExposure.none(), PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(256 * 1024),
                    DeviceExposurePolicy.none());
            var result = detection.launcher().orElseThrow().launchResolved(
                    requirement, SandboxCancellation.never());
            if (result.failure().isPresent() || result.exitCode().orElse(-1) != 0) {
                return failed(ProviderProbeResult.Status.FAILED);
            }
            List<String> lines = result.stdout().utf8().lines().toList();
            String versionLine = lines.stream().filter(line -> line.startsWith("ffmpeg version "))
                    .findFirst().orElse("");
            String configuration = lines.stream().filter(line -> line.startsWith("configuration:"))
                    .findFirst().orElse("");
            var match = VERSION.matcher(versionLine);
            if (!match.matches() || configuration.isBlank()) {
                return failed(ProviderProbeResult.Status.FAILED);
            }
            return new FfmpegRuntimeProbeResult(
                    new ProviderProbeResult(
                            FfmpegCpuProvider.BINDING, ProviderProbeResult.Status.HEALTHY),
                    Optional.of(match.group(1)),
                    Optional.of(versionLine),
                    Optional.of(configuration));
        } catch (IOException | RuntimeException failure) {
            return failed(ProviderProbeResult.Status.UNKNOWN);
        }
    }

    private static FfmpegRuntimeProbeResult failed(ProviderProbeResult.Status status) {
        return new FfmpegRuntimeProbeResult(
                new ProviderProbeResult(FfmpegCpuProvider.BINDING, status),
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
