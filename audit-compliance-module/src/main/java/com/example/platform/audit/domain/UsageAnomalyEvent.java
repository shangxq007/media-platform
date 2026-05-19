package com.example.platform.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Event emitted when a usage anomaly is detected.
 */
public record UsageAnomalyEvent(
        String eventId,
        String tenantId,
        String userId,
        String ruleType,
        String ruleName,
        String severity,
        String action,
        double score,
        Map<String, Object> context,
        OffsetDateTime detectedAt) {
}
