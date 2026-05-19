package com.example.platform.entitlement.domain;

/**
 * Quota policy defining usage limits for a tier.
 */
public record QuotaPolicy(
        String policyId,
        String tier,
        String featureCode,
        long limitValue,
        String period,
        long warningThresholdPercent) {

    public boolean isExceeded(long currentUsage) {
        return currentUsage >= limitValue;
    }

    public boolean isWarning(long currentUsage) {
        return currentUsage >= limitValue * (warningThresholdPercent / 100.0);
    }

    public long remaining(long currentUsage) {
        return Math.max(0, limitValue - currentUsage);
    }
}
