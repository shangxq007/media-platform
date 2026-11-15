package com.example.platform.bmf;

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
import com.example.platform.workerfabric.domain.providernative.ProviderNativeOutputFiles;
import com.example.platform.workerfabric.domain.providernative.SandboxExecutionPolicyResolver;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resolves the exact Artifact input and mounts the pinned BMF distribution read-only. */
public final class BmfSandboxExecutionPolicyResolver implements SandboxExecutionPolicyResolver {
    private final Path python;
    private final Path runtimeRoot;
    private final BmfSandboxWorkspace workspace;
    private final Duration timeout;
    private final long captureBytes;
    private final SandboxRuntimeCapabilities capabilities;

    public BmfSandboxExecutionPolicyResolver(Path python, Path runtimeRoot,
            BmfSandboxWorkspace workspace, Duration timeout, long captureBytes,
            SandboxRuntimeCapabilities capabilities) {
        this.python = exact(python, "python");
        this.runtimeRoot = exact(runtimeRoot, "runtimeRoot");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
        this.capabilities = java.util.Objects.requireNonNull(capabilities, "capabilities");
        if (timeout.isZero() || timeout.isNegative() || captureBytes < 1) {
            throw new IllegalArgumentException("BMF runtime limits must be positive");
        }
        this.captureBytes = captureBytes;
    }

    @Override
    public SandboxResolution resolve(ExecutionCommand command,
            List<MaterializedExecutionInput> runtimeLocalInputs) {
        if (!(command.invocationSpec() instanceof ProcessInvocationSpec invocation)
                || !invocation.executable().equals(python.toString())
                || invocation.arguments().size() != 8
                || !invocation.arguments().get(0).equals("-s")
                || !invocation.arguments().get(1).equals("-c")
                || !invocation.arguments().get(2).equals(BmfCpuRuntimeAdapter.GRAPH_PROGRAM)
                || !invocation.arguments().get(4).equals(ProviderNativeOutputFiles.RENDER_OUTPUT)
                || !isKnownOperation(invocation.arguments().get(5))
                || !isNonNegativeSeconds(invocation.arguments().get(6))
                || !isNonNegativeSeconds(invocation.arguments().get(7))
                || runtimeLocalInputs.size() != 1) {
            return rejected("BMF requires the exact graph program, one materialized input and a bounded operation");
        }
        var input = runtimeLocalInputs.getFirst();
        if (!invocation.arguments().get(3).equals(
                BmfCpuRuntimeAdapter.materializedInputToken(input.inputId()))) {
            return rejected("BMF input identity does not match the materialized Artifact");
        }
        Path inputPath = input.materializedArtifact().path().toAbsolutePath().normalize();
        if (!Files.isRegularFile(python) || !Files.isExecutable(python)
                || !Files.isDirectory(runtimeRoot.resolve("output/bmf"))
                || !Files.isRegularFile(runtimeRoot.resolve("output/bmf/lib/libbuiltin_modules.so"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3.9"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3/dist-packages/numpy"))) {
            return rejected("BMF Python or pinned runtime distribution is unavailable");
        }
        try {
            List<Path> mounts = new ArrayList<>(List.of(python, runtimeRoot, inputPath));
            mounts.sort(Comparator.comparing(Path::toString));
            int runtimeIndex = mounts.indexOf(runtimeRoot);
            Path sandboxRuntime = Path.of("/sandbox-inputs/input-" + runtimeIndex);
            Path working = workspace.outputStagingRoot().resolve("attempt-"
                    + UUID.nameUUIDFromBytes(command.platformExecutionAttemptId().value()
                            .getBytes(StandardCharsets.UTF_8)));
            List<String> args = List.of("-s", "-c", BmfCpuRuntimeAdapter.GRAPH_PROGRAM,
                    inputPath.toString(), ProviderNativeOutputFiles.RENDER_OUTPUT,
                    invocation.arguments().get(5), invocation.arguments().get(6),
                    invocation.arguments().get(7));
            var requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(Set.of(python.toString()), python.toString(), args, timeout),
                    FilesystemPolicy.exact(Set.copyOf(mounts), workspace.root(),
                            workspace.temporaryRoot(), workspace.outputStagingRoot(), working),
                    NetworkPolicy.none(),
                    EnvironmentPolicy.exact(Map.of(
                            "PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C",
                            "PYTHONHOME", sandboxRuntime.resolve("host-bundle/usr").toString(),
                            "PYTHONPATH", sandboxRuntime.resolve("output/bmf/lib") + ":"
                                    + sandboxRuntime.resolve("output") + ":"
                                    + sandboxRuntime.resolve("host-bundle/usr/lib/python3/dist-packages"),
                            "LD_LIBRARY_PATH", sandboxRuntime.resolve("host-bundle/selected-lib") + ":"
                                    + sandboxRuntime.resolve("output/bmf/lib"),
                            "BMF_MODULE_PATH", sandboxRuntime.resolve("output/bmf").toString(),
                            "PYTHONDONTWRITEBYTECODE", "1")),
                    SecretExposure.none(), PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(captureBytes), DeviceExposurePolicy.none());
            return SandboxExecutionResolver.resolve(requirement, capabilities);
        } catch (IllegalArgumentException failure) {
            return rejected("BMF sandbox policy is invalid");
        }
    }

    private static Path exact(Path path, String label) {
        java.util.Objects.requireNonNull(path, label);
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException(label + " must be absolute and normalized");
        }
        return path;
    }

    private static boolean isKnownOperation(String operation) {
        try {
            BmfOperation.ofOperationKey(operation);
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    private static boolean isNonNegativeSeconds(String value) {
        return value.matches("[0-9]+(\\.[0-9]{1,9})?");
    }

    private static SandboxResolution rejected(String reason) {
        return new SandboxResolution.Rejected(SandboxFailure.of(
                SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE, reason, Set.of()));
    }
}
