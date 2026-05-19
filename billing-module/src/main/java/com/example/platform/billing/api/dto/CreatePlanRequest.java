package com.example.platform.billing.api.dto;

import java.util.Map;

public record CreatePlanRequest(
        String planKey,
        String name,
        String description,
        String billingInterval,
        long basePriceMinor,
        String currencyCode,
        Map<String, Long> includedQuota) {
}
