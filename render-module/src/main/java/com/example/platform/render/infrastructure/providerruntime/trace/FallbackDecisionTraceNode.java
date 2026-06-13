package com.example.platform.render.infrastructure.providerruntime.trace;

import java.time.Instant;
import java.util.Map;

/**
 * Trace node for fallback decisions.
 */
public record FallbackDecisionTraceNode(
        String traceId,
        String jobId,
        String fromProvider,
        String toProvider,
        String reason,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static FallbackDecisionTraceNode create(
            String traceId,
            String jobId,
            String fromProvider,
            String toProvider,
            String reason
    ) {
        return new FallbackDecisionTraceNode(
                traceId,
                jobId,
                fromProvider,
                toProvider,
                reason,
                Instant.now(),
                Map.of()
        );
    }
}
