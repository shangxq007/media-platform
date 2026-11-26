package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.util.List;
import java.io.Closeable;
import java.io.IOException;

/** Resolves command-local enforcement policy from pre-authorized execution context. */
@FunctionalInterface
public interface SandboxExecutionPolicyResolver {
    SandboxResolution resolve(
            ExecutionCommand command, List<MaterializedExecutionInput> runtimeLocalInputs);

    /** Command-local files may use the existing read-only sandbox input mounts. */
    default PreparedResolution prepare(ExecutionCommand command, List<MaterializedExecutionInput> inputs) {
        return new PreparedResolution(resolve(command, inputs), () -> {});
    }

    record PreparedResolution(SandboxResolution resolution, Closeable inputs) implements Closeable {
        @Override public void close() throws IOException {
            inputs.close();
        }
    }
}
