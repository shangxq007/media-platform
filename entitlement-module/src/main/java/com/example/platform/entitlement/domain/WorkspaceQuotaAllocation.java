package com.example.platform.entitlement.domain;

import java.time.Instant;

public record WorkspaceQuotaAllocation(
        String id,
        String workspaceId,
        String memberId,
        String quotaProfileKey,
        long allocatedAmount,
        long usedAmount,
        String period,
        Instant createdAt,
        Instant updatedAt
) {}
