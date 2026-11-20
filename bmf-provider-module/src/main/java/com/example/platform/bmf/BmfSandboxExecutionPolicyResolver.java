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
                || invocation.arguments().size() < 4
                || !invocation.arguments().get(0).equals("-s")
                || !invocation.arguments().get(1).equals("-c")) {
            return rejected("BMF requires the exact graph program, materialized inputs and bounded timing");
        }
        var arguments = invocation.arguments();
        String program = arguments.get(2);
        boolean multitrack = BmfCpuRuntimeAdapter.MULTITRACK_GRAPH_PROGRAM.equals(program);
        boolean multiInput = multitrack || BmfCpuRuntimeAdapter.COMPOSITE_GRAPH_PROGRAM.equals(program);
        List<String> placements = List.of();
        List<String> inputTokens;
        String operation;
        String start;
        String duration;
        String rate = null;
        if (multiInput) {
            int count;
            try {
                count = Integer.parseInt(arguments.get(3));
            } catch (NumberFormatException malformed) {
                return rejected("BMF composite input count is invalid");
            }
            if (count < 1 || count > arguments.size() - 7) {
                return rejected("BMF composite input count is out of range");
            }
            int expected = 7 + count + (multitrack ? count : 0);
            if (arguments.size() != expected
                    || !arguments.get(4 + count).equals(ProviderNativeOutputFiles.RENDER_OUTPUT)
                    || !isNonNegativeSeconds(arguments.get(5 + count))
                    || !arguments.get(6 + count).matches("[1-9][0-9]*/[1-9][0-9]*")) {
                return rejected("BMF composite requires the exact program, inputs and timing");
            }
            inputTokens = List.copyOf(arguments.subList(4, 4 + count));
            operation = "composite";
            start = "0";
            duration = arguments.get(5 + count);
            rate = arguments.get(6 + count);
            if (multitrack) {
                placements = List.copyOf(arguments.subList(7 + count, expected));
                if (placements.stream().anyMatch(placement -> !validPlacement(placement, duration))) {
                    return rejected("BMF multitrack placement is invalid");
                }
            }
        } else if (BmfCpuRuntimeAdapter.GRAPH_PROGRAM.equals(program)) {
            if (arguments.size() != 8
                    || !arguments.get(4).equals(ProviderNativeOutputFiles.RENDER_OUTPUT)
                    || !isKnownOperation(arguments.get(5))
                    || !isNonNegativeSeconds(arguments.get(6))
                    || !isNonNegativeSeconds(arguments.get(7))) {
                return rejected("BMF requires the exact graph program, one materialized input and a bounded operation");
            }
            inputTokens = List.of(arguments.get(3));
            operation = arguments.get(5);
            start = arguments.get(6);
            duration = arguments.get(7);
        } else {
            return rejected("BMF requires the exact graph program");
        }
        if (runtimeLocalInputs.size() != inputTokens.size()) {
            return rejected("BMF input count does not match the materialized inputs");
        }
        Map<String, MaterializedExecutionInput> byToken = new java.util.LinkedHashMap<>();
        for (var input : runtimeLocalInputs) {
            byToken.put(BmfCpuRuntimeAdapter.materializedInputToken(input.inputId()), input);
        }
        if (byToken.size() != runtimeLocalInputs.size()
                || new java.util.HashSet<>(inputTokens).size() != inputTokens.size()
                || !byToken.keySet().containsAll(inputTokens)) {
            return rejected("BMF input identity does not match the materialized Artifact");
        }
        List<Path> mounts = new ArrayList<>(List.of(python, runtimeRoot));
        for (var token : inputTokens) {
            mounts.add(byToken.get(token).materializedArtifact().path().toAbsolutePath().normalize());
        }
        if (!Files.isRegularFile(python) || !Files.isExecutable(python)
                || !Files.isDirectory(runtimeRoot.resolve("output/bmf"))
                || !Files.isRegularFile(runtimeRoot.resolve("output/bmf/lib/libbuiltin_modules.so"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3.9"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3/dist-packages/numpy"))) {
            return rejected("BMF Python or pinned runtime distribution is unavailable");
        }
        try {
            mounts.sort(Comparator.comparing(Path::toString));
            int runtimeIndex = mounts.indexOf(runtimeRoot);
            Path sandboxRuntime = Path.of("/sandbox-inputs/input-" + runtimeIndex);
            Path working = workspace.outputStagingRoot().resolve("attempt-"
                    + UUID.nameUUIDFromBytes(command.platformExecutionAttemptId().value()
                            .getBytes(StandardCharsets.UTF_8)));
            List<String> args = new ArrayList<>(List.of("-s", "-c", program));
            if (multiInput) {
                args.add(Integer.toString(inputTokens.size()));
                for (var token : inputTokens) {
                    Path inputPath = byToken.get(token).materializedArtifact().path().toAbsolutePath().normalize();
                    args.add("/sandbox-inputs/input-" + mounts.indexOf(inputPath));
                }
                args.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
                args.add(duration);
                args.add(rate);
                args.addAll(placements);
            } else {
                Path inputPath = byToken.get(inputTokens.getFirst())
                        .materializedArtifact().path().toAbsolutePath().normalize();
                args.add("/sandbox-inputs/input-" + mounts.indexOf(inputPath));
                args.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
                args.add(operation);
                args.add(start);
                args.add(duration);
            }
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
            return !operation.equals(BmfOperation.MULTITRACK_COMPOSITE.operationKey());
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    private static boolean validPlacement(String placement, String duration) {
        String[] parts = placement.split(",", -1);
        if (parts.length != 16 || !List.of("V", "A").contains(parts[0])
                || !isNonNegativeSeconds(parts[1]) || !isNonNegativeSeconds(parts[2])) {
            return false;
        }
        var start = new java.math.BigDecimal(parts[1]);
        var end = new java.math.BigDecimal(parts[2]);
        if (start.compareTo(end) >= 0 || end.compareTo(new java.math.BigDecimal(duration)) > 0) {
            return false;
        }
        try {
            var gain = new java.math.BigDecimal(parts[3]);
            var pan = new java.math.BigDecimal(parts[5]);
            if (gain.compareTo(java.math.BigDecimal.ZERO) < 0
                    || gain.compareTo(new java.math.BigDecimal("2")) > 0
                    || !(parts[4].equals("0") || parts[4].equals("1"))
                    || pan.compareTo(new java.math.BigDecimal("-1")) < 0
                    || pan.compareTo(java.math.BigDecimal.ONE) > 0) {
                return false;
            }
            var scaleX = new java.math.BigDecimal(parts[8]);
            var scaleY = new java.math.BigDecimal(parts[9]);
            var opacity = new java.math.BigDecimal(parts[15]);
            var left = new java.math.BigDecimal(parts[11]);
            var right = new java.math.BigDecimal(parts[12]);
            var top = new java.math.BigDecimal(parts[13]);
            var bottom = new java.math.BigDecimal(parts[14]);
            return scaleX.compareTo(java.math.BigDecimal.ZERO) > 0
                    && scaleX.compareTo(java.math.BigDecimal.TEN) <= 0
                    && scaleY.compareTo(java.math.BigDecimal.ZERO) > 0
                    && scaleY.compareTo(java.math.BigDecimal.TEN) <= 0
                    && opacity.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && opacity.compareTo(java.math.BigDecimal.ONE) <= 0
                    && left.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && right.compareTo(java.math.BigDecimal.ONE) <= 0
                    && top.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && bottom.compareTo(java.math.BigDecimal.ONE) <= 0
                    && left.compareTo(right) < 0 && top.compareTo(bottom) < 0;
        } catch (NumberFormatException invalid) {
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
