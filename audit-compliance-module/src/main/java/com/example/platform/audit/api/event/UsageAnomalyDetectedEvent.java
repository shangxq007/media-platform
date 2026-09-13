package com.example.platform.audit.api.event;

import java.time.Instant;
import java.util.Map;

/**
 * Published when a user's usage exceeds an anomaly threshold.
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
