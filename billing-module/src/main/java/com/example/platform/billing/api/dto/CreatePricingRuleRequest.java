package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.List;

public record CreatePricingRuleRequest(
        String ruleKey,
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
