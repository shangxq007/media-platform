package com.example.platform.render.domain.execution;

import com.example.platform.outbox.app.ExecutionResult;
import java.util.List;

/**
 * Result of executing an execution pipeline.
 */
public record ExecutionPipelineResult(
        boolean success,
        String planId,
        ExecutionResult executionResult,
        List<String> producedProductIds,
        List<String> warnings,
        String error,
        long executionDurationMs) {

    public static ExecutionPipelineResult success(String planId, ExecutionResult result,
                                                    List<String> products, long durMs) {
        return new ExecutionPipelineResult(true, planId, result, products, List.of(), null, durMs);
    }

    public static ExecutionPipelineResult failure(String planId, String error, long durMs) {
        return new ExecutionPipelineResult(false, planId, null, List.of(), List.of(), error, durMs);
    }
}
