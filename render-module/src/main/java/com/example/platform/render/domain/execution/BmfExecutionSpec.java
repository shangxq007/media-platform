package com.example.platform.render.domain.execution;

import java.util.List;
import java.util.Map;

/**
 * BMF execution spec — graph-oriented for media processing backends.
 * Carries BMF graph definition, inputs, outputs, and options.
 */
public record BmfExecutionSpec(
        String executionSpecId,
        String backendId,
        String backendType,
        String producerId,
        List<String> inputProductIds,
        List<ExecutionInput> materializedInputs,
        List<ExecutionOutput> expectedOutputs,
        Map<String, String> executionHints,
        String workingDirectory,
        String graphDefinition,
        List<ExecutionInput> graphInputs,
        List<ExecutionOutput> graphOutputs,
        Map<String, Object> graphOptions) implements BackendExecutionSpec {

    public static BmfExecutionSpec of(String backendId, String producerId,
                                        String graphDefinition,
                                        List<ExecutionInput> inputs,
                                        List<ExecutionOutput> outputs) {
        return new BmfExecutionSpec("bmf_" + System.currentTimeMillis(),
                backendId, "bmf", producerId, List.of(), inputs, outputs,
                Map.of(), null, graphDefinition, inputs, outputs, Map.of());
    }
}
