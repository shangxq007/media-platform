package com.example.platform.billing.domain;

import java.time.Instant;

public record CustomPricingRule(
        String ruleId,
        String tenantId,
        String workspaceId,
        String meterKey,
        Long overridePriceMinor,
        Double discountPercent,
        Instant effectiveFrom,
        Instant effectiveTo,
        String status,
        Instant createdAt) {
}
