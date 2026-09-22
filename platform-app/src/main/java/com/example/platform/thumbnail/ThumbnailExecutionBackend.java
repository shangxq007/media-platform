package com.example.platform.thumbnail;

import com.example.platform.sandbox.*;
import com.example.platform.sandbox.execution.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Registered local WorkerRuntime backend for the pinned thumbnail provider. */
@Component
public final class ThumbnailExecutionBackend implements ExecutionBackend {
    @Override public String backendId() { return "thumbnail-worker-runtime.local-process"; }
    @Override public boolean supports(TaskCapability capability) { return capability == TaskCapability.THUMBNAIL; }
    @Override public ExecutionResult execute(ExecutionRequest request) {
        try {
            Path work = Path.of(request.workingDirectory()).toAbsolutePath().normalize();
            Path executable = Path.of(request.payload().get("executable")).toAbsolutePath().normalize();
            Path input = Path.of(request.payload().get("input")).toAbsolutePath().normalize();
            var detection = BubblewrapSandboxCapabilityDetector.detect();
            if (detection.launcher().isEmpty()) return ExecutionResult.failure(-1, "bubblewrap unavailable", 0, "SANDBOX_UNAVAILABLE", "bubblewrap unavailable");
            var requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(Set.of(executable.toString()), executable.toString(), request.arguments(), Duration.ofSeconds(request.timeoutSeconds())),
                    FilesystemPolicy.exact(Set.of(executable, input), work, work.resolve(".sandbox-tmp"), work.resolve(".sandbox-output"), work),
                    NetworkPolicy.none(), EnvironmentPolicy.exact(Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C")),
                    SecretExposure.none(), PrivilegePolicy.unprivileged(), ResourceEnforcementLimits.captureOnly(64L * 1024L * 1024L), DeviceExposurePolicy.none());
            var result = detection.launcher().orElseThrow().launchResolved(requirement, SandboxCancellation.never());
            return result.failure().isPresent()
                    ? ExecutionResult.failure(result.exitCode().orElse(-1), result.stderr().utf8(), 0, result.failure().get().code().name(), result.failure().get().message())
                    : ExecutionResult.success(result.exitCode().orElse(0), result.stdout().utf8(), result.stderr().utf8(), 0);
        } catch (Exception e) { return ExecutionResult.failure(-1, e.getMessage(), 0, "BACKEND_FAILED", e.getMessage()); }
    }
}
