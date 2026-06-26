package com.example.platform.render.domain.planner;

import java.util.List;
import java.util.Map;

/**
 * A single step in an execution stage — maps to one Producer invocation.
 * Includes backend selection metadata.
 */
public record ExecutionStep(
        String stepId,
        String producerId,
        List<String> inputProductIds,
        List<String> expectedOutputTypes,
        Map<String, String> executionHints,
        String backendId,
        String backendType,
        String backendSelectionReason,
        boolean backendResolved) {

    public static ExecutionStep of(String producerId, List<String> inputs, List<String> outputs) {
        return new ExecutionStep("step_" + System.currentTimeMillis(), producerId,
                inputs, outputs, Map.of(), null, null, null, false);
    }

    public ExecutionStep withBackend(String backendId, String backendType, String reason) {
        return new ExecutionStep(stepId, producerId, inputProductIds, expectedOutputTypes,
                executionHints, backendId, backendType, reason, true);
    }
}
