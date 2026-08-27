package com.example.platform.ffmpeg;

import com.example.platform.sandbox.DeviceExposurePolicy;
import com.example.platform.sandbox.EnvironmentPolicy;
import com.example.platform.sandbox.FilesystemPolicy;
import com.example.platform.sandbox.NetworkPolicy;
import com.example.platform.sandbox.PrivilegePolicy;
import com.example.platform.sandbox.ProcessRequirement;
import com.example.platform.sandbox.ResourceEnforcementLimits;
import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SandboxExecutionResolver;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.sandbox.SecretExposure;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.SandboxExecutionPolicyResolver;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves the one exact materialized input into a fixed FFmpeg argv under sandbox policy. */
public final class FfmpegSandboxExecutionPolicyResolver
        implements SandboxExecutionPolicyResolver {

    private final Path executable;
    private final FfmpegSandboxWorkspace workspace;
    private final Duration timeout;
    private final long captureBytes;
    private final SandboxRuntimeCapabilities capabilities;

    public FfmpegSandboxExecutionPolicyResolver(
            Path executable,
            FfmpegSandboxWorkspace workspace,
            Duration timeout,
            long captureBytes,
            SandboxRuntimeCapabilities capabilities) {
        this.executable = Objects.requireNonNull(executable, "executable")
                .toAbsolutePath().normalize();
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        if (!executable.isAbsolute() || !executable.equals(this.executable)
                || timeout.isZero() || timeout.isNegative() || captureBytes < 1) {
            throw new IllegalArgumentException("FFmpeg sandbox settings must be bounded and normalized");
        }
        this.captureBytes = captureBytes;
    }

    @Override
    public SandboxResolution resolve(
            ExecutionCommand command, List<MaterializedExecutionInput> runtimeLocalInputs) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(runtimeLocalInputs, "runtimeLocalInputs");
        if (!(command.invocationSpec() instanceof ProcessInvocationSpec invocation)
                || !invocation.executable().equals(executable.toString())
                || runtimeLocalInputs.size() != 1) {
            return rejected("FFmpeg requires one exact typed process and materialized input");
        }
        MaterializedExecutionInput input = runtimeLocalInputs.getFirst();
        String token = FfmpegCpuRuntimeAdapter.materializedInputToken(input.inputId());
        int replacements = 0;
        List<String> arguments = new ArrayList<>(invocation.arguments().size());
        for (String argument : invocation.arguments()) {
            if (argument.equals(token)) {
                arguments.add(input.materializedArtifact().path().toAbsolutePath().normalize().toString());
                replacements++;
            } else {
                arguments.add(argument);
            }
        }
        if (replacements != 1) {
            return rejected("FFmpeg argv must contain exactly one exact materialized input token");
        }
        try {
            SandboxExecutionRequirement requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(
                            Set.of(executable.toString()),
                            executable.toString(),
                            arguments,
                            timeout),
                    FilesystemPolicy.exact(
                            Set.of(executable, input.materializedArtifact().path()),
                            workspace.root(),
                            workspace.temporaryRoot(),
                            workspace.outputStagingRoot(),
                            workspace.workingDirectory()),
                    NetworkPolicy.none(),
                    EnvironmentPolicy.exact(Map.of(
                            "PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C")),
                    SecretExposure.none(),
                    PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(captureBytes),
                    DeviceExposurePolicy.none());
            return SandboxExecutionResolver.resolve(requirement, capabilities);
        } catch (IllegalArgumentException failure) {
            return rejected("FFmpeg filesystem or process policy is invalid");
        }
    }

    private static SandboxResolution rejected(String message) {
        return new SandboxResolution.Rejected(SandboxFailure.of(
                SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE, message, Set.of()));
    }
}
