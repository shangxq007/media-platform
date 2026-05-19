package com.example.platform.billing.domain;

import java.time.Instant;
import java.util.Map;

public record SubscriptionContract(
        String contractId,
        String tenantId,
        String userId,
        String planKey,
        Instant periodStartAt,
        Instant periodEndAt,
        String lifecycleState,
        long basePriceMinor,
        String currencyCode,
        Map<String, Long> includedQuota,
        Map<String, Long> includedQuotaUsed) {
}
