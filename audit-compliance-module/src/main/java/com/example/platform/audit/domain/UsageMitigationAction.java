package com.example.platform.audit.domain;

import java.time.OffsetDateTime;

/**
 * Mitigation action taken in response to an anomaly.
 */
public record UsageMitigationAction(
        String actionId,
        String tenantId,
        String userId,
        String ruleType,
        String actionType,
        String reason,
        String targetPreset,
        boolean applied,
        OffsetDateTime appliedAt) {

    public static final String ACTION_OBSERVE = "OBSERVE";
    public static final String ACTION_WARN = "WARN";
    public static final String ACTION_SOFT_LIMIT = "SOFT_LIMIT";
    public static final String ACTION_DEGRADE = "DEGRADE";
    public static final String ACTION_HARD_BLOCK = "HARD_BLOCK";
    public static final String ACTION_REQUIRE_REVIEW = "REQUIRE_REVIEW";
}
