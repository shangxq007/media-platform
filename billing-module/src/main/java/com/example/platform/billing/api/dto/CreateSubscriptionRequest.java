package com.example.platform.billing.api.dto;

import java.time.Instant;

public record CreateSubscriptionRequest(
        String tenantId,
        String userId,
        String contractId,
        String planKey,
        int periodDays,
        String idempotencyKey,
        String actor,
        String reason,
        String traceId,
        Instant effectiveAt) {
}
