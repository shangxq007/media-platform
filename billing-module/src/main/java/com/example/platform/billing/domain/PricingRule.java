package com.example.platform.billing.domain;

import java.time.Instant;
import java.util.List;

public record PricingRule(
        String ruleId,
        String ruleKey,
        String name,
        String description,
        PricingModel pricingModel,
        String meterKey,
        long unitPriceMinor,
        String currencyCode,
        List<PricingTier> tiers,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt) {
}
