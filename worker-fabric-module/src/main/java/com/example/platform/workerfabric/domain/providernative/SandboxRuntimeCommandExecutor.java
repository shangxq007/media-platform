package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.sandbox.BoundedProcessLauncher;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxProcessExecutionException;
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
        byte[] candidateOutput = null;
        for (ExecutionCommand command : bundle.commands()) {
            if (!(command.invocationSpec() instanceof ProcessInvocationSpec)) {
                throw new SandboxProcessExecutionException(SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE,
                        "only typed process invocation is supported", java.util.Set.of()));
            }
            SandboxResolution resolution = specifications.resolve(command, inputs);
            if (resolution instanceof SandboxResolution.Rejected rejected) {
                throw new SandboxProcessExecutionException(rejected.failure());
            }
            SandboxExecutionResult result = launcher.launch(
                    ((SandboxResolution.Resolved) resolution).specification(), cancellation);
            if (result.failure().isPresent()) {
                throw new SandboxProcessExecutionException(result.failure().orElseThrow());
            }
            candidateOutput = result.stdout().bytes();
        }
        if (candidateOutput == null || candidateOutput.length == 0) {
            throw new SandboxProcessExecutionException(SandboxFailure.of(
                    SandboxFailureCode.OUTPUT_STAGING_FAILED,
                    "process success produced no candidate output bytes", java.util.Set.of()));
        }
        return new ProviderExecutionOutput(new ByteArrayInputStream(candidateOutput));
    }
}
