package com.example.platform.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Alert generated from anomaly detection.
 */
public record UsageAlert(
        String alertId,
        String tenantId,
        String userId,
        String ruleType,
        String severity,
        String message,
        String action,
        Map<String, Object> details,
        boolean acknowledged,
        OffsetDateTime createdAt) {
}
