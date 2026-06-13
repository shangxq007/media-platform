package com.example.platform.render.infrastructure.providerruntime.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Trace node for provider decisions.
 */
public record ProviderDecisionTraceNode(
        String traceId,
        String jobId,
        String selectedProvider,
        List<String> candidates,
        String selectionReason,
        boolean providerFound,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public static ProviderDecisionTraceNode create(
            String traceId,
            String jobId,
            String selectedProvider,
            List<String> candidates,
            String selectionReason,
            boolean providerFound
    ) {
        return new ProviderDecisionTraceNode(
                traceId,
                jobId,
                selectedProvider,
                candidates,
                selectionReason,
                providerFound,
                Instant.now(),
                Map.of()
        );
    }
}
