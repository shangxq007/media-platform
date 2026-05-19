package com.example.platform.entitlement.domain;

import java.time.Instant;

public record EntitlementGrant(
        String grantId,
        String tenantId,
        String workspaceId,
        String subjectType,
        String subjectId,
        String featureKey,
        String bundleKey,
        String quotaProfileKey,
        String source,
        String reason,
        String grantedBy,
        Instant startsAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokedBy,
        String revokeReason,
        EntitlementGrantStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
