package com.example.platform.quota.app;

/**
 * Application-layer view of a quota bucket state.
 *
 * <p>This type is part of the public API of the quota module and may be used by
 * other modules without depending on {@code quota.domain.QuotaBucket}.
 */
public record QuotaBucketSummary(
        String featureCode,
        long currentUsage,
        long limit,
        double usageRatio,
        boolean exceeded
) {
    /** Convenience: bucket is close to but not yet at the limit (>= 80%). */
    public boolean isNearLimit() {
        return usageRatio >= 0.8 && !exceeded;
    }
}
