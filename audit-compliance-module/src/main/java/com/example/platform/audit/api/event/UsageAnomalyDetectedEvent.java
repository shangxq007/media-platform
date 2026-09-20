package com.example.platform.audit.api.event;

import java.time.Instant;
import java.util.Map;

/**
 * Durable observation that this detector invocation exceeded a usage threshold.
 * The Outbox fact is the durable record; process-local risk/mitigation views are derived.
 * This does not assert that a render submission or mitigation was durably executed.
 * A new invocation is a new observation; delivery retries retain the original eventId.
 */
public record UsageAnomalyDetectedEvent(
        String eventId,
        String tenantId,
        String userId,
        String ruleType,
        String severity,
        String action,
        double score,
        Map<String, Object> details,
        Instant detectedAt) {
    public UsageAnomalyDetectedEvent {
        if(eventId==null||eventId.isBlank()||tenantId==null||tenantId.isBlank()||userId==null||userId.isBlank()||!Double.isFinite(score))
            throw new IllegalArgumentException("Scoped anomaly observation required");
        java.util.Objects.requireNonNull(detectedAt); details=Map.copyOf(java.util.Objects.requireNonNull(details));
    }
}
