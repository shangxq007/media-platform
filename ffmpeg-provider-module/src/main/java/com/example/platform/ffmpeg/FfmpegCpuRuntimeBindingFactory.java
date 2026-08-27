package com.example.platform.ffmpeg;

import com.example.platform.sandbox.BoundedCapture;
import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.BubblewrapSandboxDetection;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxCleanupObservation;
import com.example.platform.sandbox.SandboxExecutionHandle;
import com.example.platform.sandbox.SandboxExecutionObservation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.workerfabric.domain.providernative.SandboxRuntimeCommandExecutor;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Composes the concrete provider only through the accepted generic runtime and sandbox seams. */
public final class FfmpegCpuRuntimeBindingFactory {

    private FfmpegCpuRuntimeBindingFactory() {}

    public static ProviderNativeRuntimeBinding<FfmpegCpuTranscodePlan> create(
            Path executable,
            FfmpegSandboxWorkspace workspace,
            Duration timeout,
            long captureBytes,
            SandboxCancellation cancellation) {
        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        SandboxRuntimeCapabilities capabilities = detection.launcher()
                .map(launcher -> launcher.capabilities())
                .orElseGet(() -> SandboxRuntimeCapabilities.unavailable("bubblewrap-probe"));
        var launcher = detection.launcher()
                .<com.example.platform.sandbox.BoundedProcessLauncher>map(value -> value)
                .orElse((specification, ignored) -> unavailable(
                        specification.filesystem().workingDirectory(), detection.diagnostic()));
        return new ProviderNativeRuntimeBinding<>(
                new FfmpegCpuTranscodeLowerer(),
                new FfmpegCpuRuntimeAdapter(executable),
                new SandboxRuntimeCommandExecutor(
                        launcher,
                        new FfmpegSandboxExecutionPolicyResolver(
                                executable, workspace, timeout, captureBytes, capabilities),
                        cancellation));
    }

    private static SandboxExecutionResult unavailable(Path workingDirectory, String diagnostic) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(
                OptionalInt.empty(),
                new BoundedCapture(new byte[0], false),
                new BoundedCapture(new byte[0], false),
                Optional.of(SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_UNAVAILABLE, diagnostic, Set.of())),
                new SandboxExecutionObservation(
                        new SandboxExecutionHandle(-1, now),
                        workingDirectory,
                        Duration.ZERO,
                        new SandboxCleanupObservation(true, 0, List.of(), "")));
    }
}
