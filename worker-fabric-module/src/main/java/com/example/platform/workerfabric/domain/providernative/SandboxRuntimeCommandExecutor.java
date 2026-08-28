package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.sandbox.BoundedProcessLauncher;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Worker-owned command composition that routes process mechanics through the neutral sandbox boundary. */
public final class SandboxRuntimeCommandExecutor implements RuntimeCommandExecutor {
    private final BoundedProcessLauncher launcher;
    private final SandboxExecutionPolicyResolver specifications;
    private final SandboxCancellation cancellation;

    public SandboxRuntimeCommandExecutor(
            BoundedProcessLauncher launcher,
            SandboxExecutionPolicyResolver specifications,
            SandboxCancellation cancellation) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.specifications = Objects.requireNonNull(specifications, "specifications");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    @Override
    public ProviderExecutionOutput execute(
            RuntimeExecutionBundle bundle, List<MaterializedExecutionInput> inputs) throws IOException {
        try {
            byte[] candidateOutput = null;
            for (ExecutionCommand command : bundle.commands()) {
                if (!(command.invocationSpec() instanceof ProcessInvocationSpec)) {
                    throw mapped(SandboxFailure.of(
                            SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE,
                            "only typed process invocation is supported", java.util.Set.of()));
                }
                SandboxResolution resolution = specifications.resolve(command, inputs);
                if (resolution instanceof SandboxResolution.Rejected rejected) {
                    throw mapped(rejected.failure());
                }
                SandboxExecutionResult result = launcher.launch(
                        ((SandboxResolution.Resolved) resolution).specification(), cancellation);
                if (result.failure().isPresent()) {
                    throw mapped(result);
                }
                if (result.stdout().truncated()) {
                    throw new ProviderNativeExecutionFailure(
                            ProviderNativeFailureCode.PROCESS_OUTPUT_TRUNCATED,
                            "process stdout exceeded bounded capture limit");
                }
                candidateOutput = result.stdout().bytes();
            }
            if (candidateOutput == null || candidateOutput.length == 0) {
                throw mapped(SandboxFailure.of(
                        SandboxFailureCode.OUTPUT_STAGING_FAILED,
                        "process success produced no candidate output bytes", java.util.Set.of()));
            }
            return new ProviderExecutionOutput(new ByteArrayInputStream(candidateOutput));
        } catch (ProviderNativeExecutionFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN,
                    "sandbox runtime execution failed without typed process evidence",
                    java.util.Map.of("failureType", failure.getClass().getSimpleName()));
        }
    }

    private static ProviderNativeExecutionFailure mapped(SandboxFailure failure) {
        return mapped(failure, java.util.Map.of(
                "sandboxFailureCode", failure.code().name()));
    }

    private static ProviderNativeExecutionFailure mapped(SandboxExecutionResult result) {
        SandboxFailure failure = result.failure().orElseThrow();
        return mapped(failure, java.util.Map.of(
                "sandboxFailureCode", failure.code().name(),
                "processExitCode", result.exitCode().isPresent()
                        ? Integer.toString(result.exitCode().getAsInt())
                        : "NOT_AVAILABLE",
                "boundedStderr", result.stderr().utf8(),
                "stderrTruncated", Boolean.toString(result.stderr().truncated()),
                "boundedStdoutSize", Integer.toString(result.stdout().bytes().length),
                "stdoutTruncated", Boolean.toString(result.stdout().truncated())));
    }

    private static ProviderNativeExecutionFailure mapped(
            SandboxFailure failure, java.util.Map<String, String> diagnostics) {
        ProviderNativeFailureCode code = switch (failure.code()) {
            case PROCESS_LAUNCH_FAILED -> ProviderNativeFailureCode.PROCESS_LAUNCH_FAILED;
            case PROCESS_CRASHED -> ProviderNativeFailureCode.PROCESS_NONZERO_EXIT;
            case PROCESS_TERMINATED_BY_LIMIT -> ProviderNativeFailureCode.PROCESS_CANCELLED;
            case PROCESS_TIMEOUT -> ProviderNativeFailureCode.PROCESS_TIMEOUT;
            case SANDBOX_CLEANUP_FAILED -> ProviderNativeFailureCode.PROCESS_CLEANUP_FAILED;
            case OUTPUT_STAGING_FAILED -> ProviderNativeFailureCode.EMPTY_PROVIDER_OUTPUT;
            case SANDBOX_RUNTIME_LOST -> ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN;
            default -> ProviderNativeFailureCode.SANDBOX_POLICY_REJECTED;
        };
        return new ProviderNativeExecutionFailure(
                code,
                failure.message(),
                diagnostics);
    }
}
