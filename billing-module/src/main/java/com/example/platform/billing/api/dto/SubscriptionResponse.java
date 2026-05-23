package com.example.platform.billing.api.dto;

import java.time.Instant;

public record SubscriptionResponse(
        String contractId,
        String tenantId,
        String userId,
        String planKey,
        Instant periodStartAt,
        Instant periodEndAt,
        String lifecycleState,
        long basePriceMinor,
        String currencyCode,
        String contractRole,
        String productCode) {
}
