package com.example.platform.billing.domain;

import java.time.Instant;
import java.util.Map;

public record RatedUsageRecord(
        String ratedUsageId,
        String usageRecordId,
        String pricingRuleId,
        long ratedAmountMinor,
        String currencyCode,
        Map<String, Object> ratingDetails,
        Instant createdAt) {
}
