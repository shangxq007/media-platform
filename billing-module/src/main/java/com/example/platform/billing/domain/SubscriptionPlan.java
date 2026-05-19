package com.example.platform.billing.domain;

import java.time.Instant;
import java.util.Map;

public record SubscriptionPlan(
        String planId,
        String planKey,
        String name,
        String description,
        String billingInterval,
        long basePriceMinor,
        String currencyCode,
        Map<String, Long> includedQuota,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
