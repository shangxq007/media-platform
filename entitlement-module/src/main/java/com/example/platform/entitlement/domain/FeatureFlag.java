package com.example.platform.entitlement.domain;

/**
 * Feature flag for gating features by tenant, user, or tier.
 */
public record FeatureFlag(
        String flagKey,
        String displayName,
        boolean enabled,
        String scope,
        String targetTier,
        String description) {

    public boolean isEnabledFor(String tier) {
        if (!enabled) return false;
        if (targetTier == null || targetTier.isEmpty()) return true;
        return targetTier.equalsIgnoreCase(tier);
    }
}
