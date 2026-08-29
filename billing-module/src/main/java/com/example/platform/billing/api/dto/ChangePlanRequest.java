package com.example.platform.billing.api.dto;

import java.time.Instant;

public record ChangePlanRequest(
        String tenantId,
        String userId,
        String contractId,
        String newPlanKey,
        int periodDays,
        long expectedVersion,
        String idempotencyKey,
        String actor,
        String reason,
        String traceId,
        Instant effectiveAt) {
}
