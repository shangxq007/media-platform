package com.example.platform.render.infrastructure.providerruntime.trace;

import java.time.Instant;
import java.util.Map;

/**
 * Trace node for execution steps.
 */
public record ExecutionStepTraceNode(
        String traceId,
        String jobId,
        String providerName,
        boolean success,
        String error,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static ExecutionStepTraceNode create(
            String traceId,
            String jobId,
            String providerName,
            boolean success,
            String error
    ) {
        return new ExecutionStepTraceNode(
                traceId,
                jobId,
                providerName,
                success,
                error,
                Instant.now(),
                Map.of()
        );
    }
}
