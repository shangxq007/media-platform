package com.example.platform.entitlement.domain;

import java.time.Instant;
import java.util.List;

public record EntitlementDecision(
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
        boolean requiresReview
) {}
