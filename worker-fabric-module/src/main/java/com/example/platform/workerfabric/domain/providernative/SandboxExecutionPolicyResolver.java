package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.util.List;

/** Resolves command-local enforcement policy from pre-authorized execution context. */
@FunctionalInterface
public interface SandboxExecutionPolicyResolver {
    SandboxResolution resolve(
            ExecutionCommand command, List<MaterializedExecutionInput> runtimeLocalInputs);
}
