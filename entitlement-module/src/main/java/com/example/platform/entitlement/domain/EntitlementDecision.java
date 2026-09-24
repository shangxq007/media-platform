package com.example.platform.entitlement.domain;

import java.time.Instant;
import java.math.BigDecimal;
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
        BigDecimal quotaRemaining,
        String recommendedAlternative,
        List<String> upgradeOptions,
        Instant expiresAt,
        boolean requiresReview
) {}
