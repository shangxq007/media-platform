package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.List;

public record CreatePricingRuleRequest(
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
        Instant effectiveFrom,
        Instant effectiveTo) {
}
