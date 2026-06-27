package com.example.platform.render.domain.execution;

import java.util.List;
import java.util.Map;

/**
 * Remotion-specific execution spec — carries composition metadata.
 * Backend-specific. Never leaks into Platform Kernel.
 */
public record RemotionExecutionSpec(
        String executionSpecId,
        String backendId,
        String backendType,
        String producerId,
        List<String> inputProductIds,
        List<ExecutionInput> materializedInputs,
        List<ExecutionOutput> expectedOutputs,
        Map<String, String> executionHints,
        String entryFile,
        Map<String, Object> props,
        String frameRange,
        String outputFormat,
        Map<String, String> renderOptions) implements BackendExecutionSpec {

    public static RemotionExecutionSpec of(String producerId, String entryFile,
                                             List<ExecutionInput> inputs) {
        return new RemotionExecutionSpec("remsp-" + System.currentTimeMillis(),
                "remotion-process", "MEDIA_PIPELINE", producerId, List.of(),
                inputs, List.of(ExecutionOutput.of("PREVIEW", "MEDIA_FILE")),
                Map.of(), entryFile, Map.of(), "0-300", "mp4", Map.of());
    }
}
