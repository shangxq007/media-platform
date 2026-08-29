package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.List;

public record PricingRuleResponse(
        String ruleId,
        String tenantId,
        String ruleKey,
        long ruleVersion,
        String name,
        String description,
        String pricingModel,
        String meterKey,
        long unitPriceMinor,
        String currencyCode,
        List<PricingTierDto> tiers,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt) {
}
