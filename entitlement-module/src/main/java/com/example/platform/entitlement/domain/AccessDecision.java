package com.example.platform.entitlement.domain;

import com.example.platform.policy.featureflag.domain.FeatureFlagDecision;

import java.time.Instant;
import java.util.List;

public record AccessDecision(
        boolean allowed,
        String decision,
        String reasonCode,
        String userFriendlyMessage,
        String currentTier,
        List<String> matchedPolicies,
        String matchedGrantId,
        String matchedOverrideId,
        String matchedWorkspacePoolId,
        Long quotaRemaining,
        String recommendedAlternative,
        List<String> upgradeOptions,
        Instant expiresAt,
        boolean requiresReview,
        List<FeatureFlagDecision> matchedFeatureFlags,
        boolean disabledByFeatureFlag,
        List<String> featureFlagReasons
) {
    public AccessDecision(boolean allowed, String decision, String reasonCode,
                          String userFriendlyMessage, String currentTier,
                          List<String> matchedPolicies, String matchedGrantId,
                          String matchedOverrideId, String matchedWorkspacePoolId,
                          Long quotaRemaining, String recommendedAlternative,
                          List<String> upgradeOptions, Instant expiresAt,
                          boolean requiresReview) {
        this(allowed, decision, reasonCode, userFriendlyMessage, currentTier,
                matchedPolicies, matchedGrantId, matchedOverrideId, matchedWorkspacePoolId,
                quotaRemaining, recommendedAlternative, upgradeOptions, expiresAt,
                requiresReview, List.of(), false, List.of());
    }
}
