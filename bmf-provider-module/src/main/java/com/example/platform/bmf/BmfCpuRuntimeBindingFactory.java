package com.example.platform.bmf;

import com.example.platform.sandbox.BoundedCapture;
import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
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

/** Composes BMF through the same native Worker and bubblewrap process boundary as FFmpeg. */
public final class BmfCpuRuntimeBindingFactory {
    private BmfCpuRuntimeBindingFactory() {}

    public static ProviderNativeRuntimeBinding<BmfCpuTranscodePlan> create(
            Path python, Path runtimeRoot, Path workspaceRoot, Duration timeout,
            long captureBytes, SandboxCancellation cancellation) {
        var workspace = BmfSandboxWorkspace.under(workspaceRoot);
        var detection = BubblewrapSandboxCapabilityDetector.detect();
        SandboxRuntimeCapabilities capabilities = detection.launcher()
                .map(value -> value.capabilities())
                .orElseGet(() -> SandboxRuntimeCapabilities.unavailable("bubblewrap-probe"));
        var launcher = detection.launcher()
                .<com.example.platform.sandbox.BoundedProcessLauncher>map(value -> value)
                .orElse((specification, ignored) -> unavailable(
                        specification.filesystem().workingDirectory(), detection.diagnostic()));
        return new ProviderNativeRuntimeBinding<>(new BmfCpuTranscodeLowerer(),
                new BmfCpuRuntimeAdapter(python),
                new SandboxRuntimeCommandExecutor(launcher,
                        new BmfSandboxExecutionPolicyResolver(python, runtimeRoot, workspace,
                                timeout, captureBytes, capabilities),
                        cancellation, workspace.outputStagingRoot()));
    }

    private static SandboxExecutionResult unavailable(Path workingDirectory, String diagnostic) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(OptionalInt.empty(), new BoundedCapture(new byte[0], false),
                new BoundedCapture(new byte[0], false),
                Optional.of(SandboxFailure.of(SandboxFailureCode.SANDBOX_UNAVAILABLE,
                        diagnostic, Set.of())),
                new SandboxExecutionObservation(new SandboxExecutionHandle(-1, now), workingDirectory,
                        Duration.ZERO, new SandboxCleanupObservation(true, 0, List.of(), "")));
    }
}
