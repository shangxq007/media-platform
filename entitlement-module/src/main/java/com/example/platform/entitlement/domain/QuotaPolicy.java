package com.example.platform.entitlement.domain;

import java.math.BigDecimal;

/**
 * Quota policy defining usage limits for a tier.
 */
public record QuotaPolicy(
        String policyId,
        String tier,
        String featureCode,
        BigDecimal limitValue,
        String period,
        BigDecimal warningThresholdPercent) {

    public QuotaPolicy {
        limitValue = QuotaQuantity.exact(limitValue, "limitValue");
        warningThresholdPercent = QuotaQuantity.exact(warningThresholdPercent, "warningThresholdPercent");
        if (limitValue.signum() < 0 || warningThresholdPercent.signum() < 0)
            throw new IllegalArgumentException("quota policy quantities must not be negative");
    }

    public boolean isExceeded(BigDecimal currentUsage) {
        return currentUsage.compareTo(limitValue) >= 0;
    }

    public boolean isWarning(BigDecimal currentUsage) {
        return currentUsage.compareTo(limitValue.multiply(warningThresholdPercent).divide(BigDecimal.valueOf(100))) >= 0;
    }

    public BigDecimal remaining(BigDecimal currentUsage) {
        return limitValue.subtract(currentUsage).max(BigDecimal.ZERO);
    }

    public QuotaPolicy(String policyId, String tier, String featureCode, long limitValue,
            String period, long warningThresholdPercent) {
        this(policyId, tier, featureCode, BigDecimal.valueOf(limitValue), period,
                BigDecimal.valueOf(warningThresholdPercent));
    }
}
