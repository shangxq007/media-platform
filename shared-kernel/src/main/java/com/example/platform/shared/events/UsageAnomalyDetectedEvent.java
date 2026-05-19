package com.example.platform.shared.events;

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
        Instant detectedAt) {}
