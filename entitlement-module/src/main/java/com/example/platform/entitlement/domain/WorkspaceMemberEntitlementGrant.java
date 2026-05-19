package com.example.platform.entitlement.domain;

import java.time.Instant;

public record WorkspaceMemberEntitlementGrant(
        String id,
        String workspaceId,
        String memberId,
        String featureKey,
        long quotaAmount,
        Instant startsAt,
        Instant expiresAt,
        String status,
        String grantedBy,
        Instant createdAt,
        Instant updatedAt
) {}
