package com.example.platform.entitlement.domain;

import java.time.Instant;

public record WorkspaceEntitlementPool(
        String id,
        String workspaceId,
        String featureKey,
        long totalQuota,
        long usedQuota,
        String period,
        Instant createdAt,
        Instant updatedAt
) {}
