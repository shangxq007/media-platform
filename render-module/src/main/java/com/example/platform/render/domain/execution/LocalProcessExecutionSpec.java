package com.example.platform.render.domain.execution;

import java.util.List;
import java.util.Map;

/**
 * Local process execution spec — executable + arguments for CLI-based backends.
 * Extends the base BackendExecutionSpec with CLI-specific fields.
 */
public record LocalProcessExecutionSpec(
        String executionSpecId,
        String backendId,
        String backendType,
        String producerId,
        List<String> inputProductIds,
        List<ExecutionInput> materializedInputs,
        List<ExecutionOutput> expectedOutputs,
        Map<String, String> executionHints,
        String workingDirectory,
        String executable,
        List<String> arguments,
        Map<String, String> environment) implements BackendExecutionSpec {

    public static LocalProcessExecutionSpec of(String backendId, String producerId,
                                                 List<ExecutionInput> inputs,
                                                 List<ExecutionOutput> outputs,
                                                 String executable, List<String> args) {
        return new LocalProcessExecutionSpec("lps_" + System.currentTimeMillis(),
                backendId, "local-process", producerId, List.of(), inputs, outputs,
                Map.of(), null, executable, args, Map.of());
    }
}
