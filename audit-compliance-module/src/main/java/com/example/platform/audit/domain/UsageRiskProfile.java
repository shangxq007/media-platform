package com.example.platform.audit.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Risk profile for a user based on usage patterns.
 */
public record UsageRiskProfile(
        String tenantId,
        String userId,
        double riskScore,
        String riskLevel,
        List<String> activeAnomalies,
        List<String> recentMitigationActions,
        Map<String, Object> usageStats,
        OffsetDateTime evaluatedAt) {
}
