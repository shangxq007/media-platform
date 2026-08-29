package com.example.platform.billing.api.dto;

import java.time.Instant;

public record CancelSubscriptionRequest(
        String tenantId,
        String userId,
        String contractId,
        long expectedVersion,
        String idempotencyKey,
        String actor,
        String reason,
        String traceId,
        Instant effectiveAt) {
}
