package com.example.platform.render.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Trace node for state machine transitions.
 * Emitted on every state transition for full observability.
 */
public record StateTransitionTraceNode(
        String traceId,
        String jobId,
        String fromState,
        String toState,
        String reason,
        String triggeredBy,
        Instant timestamp,
        Map<String, Object> metadata
) {
    /**
     * Create a new state transition trace node.
     */
    public static StateTransitionTraceNode create(
            String traceId,
            String jobId,
            RenderJobStatus from,
            RenderJobStatus to,
            String reason,
            String triggeredBy
    ) {
        return new StateTransitionTraceNode(
                traceId,
                jobId,
                from.name(),
                to.name(),
                reason,
                triggeredBy,
                Instant.now(),
                Map.of(
                        "fromOrdinal", from.ordinal(),
                        "toOrdinal", to.ordinal(),
                        "isTerminal", to.isTerminal(),
                        "isActive", to.isActive()
                )
        );
    }

    /**
     * Create with additional metadata.
     */
    public static StateTransitionTraceNode createWithMetadata(
            String traceId,
            String jobId,
            RenderJobStatus from,
            RenderJobStatus to,
            String reason,
            String triggeredBy,
            Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("fromOrdinal", from.ordinal());
        metadata.put("toOrdinal", to.ordinal());
        metadata.put("isTerminal", to.isTerminal());
        metadata.put("isActive", to.isActive());
        metadata.putAll(extraMetadata);

        return new StateTransitionTraceNode(
                traceId,
                jobId,
                from.name(),
                to.name(),
                reason,
                triggeredBy,
                Instant.now(),
                Map.copyOf(metadata)
        );
    }

    /**
     * Get a human-readable description of the transition.
     */
    public String getDescription() {
        return String.format("[%s] %s -> %s: %s (by %s)",
                traceId != null ? traceId.substring(0, Math.min(8, traceId.length())) : "no-trace",
                fromState, toState, reason, triggeredBy);
    }
}
