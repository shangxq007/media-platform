package com.example.platform.billing.domain;

import com.example.platform.billing.usage.BillableUsage;
import java.time.Instant;

public record RateUsageCommand(
        BillableUsage billableUsage, String pricingRuleKey, long pricingRuleVersion,
        String idempotencyKey, Instant ratedAt, String traceId) {

    public RateUsageCommand {
        if (billableUsage == null) throw new IllegalArgumentException("billableUsage is required");
        if (pricingRuleKey == null || pricingRuleKey.isBlank()) throw new IllegalArgumentException("pricingRuleKey is required");
        if (pricingRuleVersion <= 0) throw new IllegalArgumentException("pricingRuleVersion must be positive");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (ratedAt == null) throw new IllegalArgumentException("ratedAt is required");
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId is required");
    }
}
