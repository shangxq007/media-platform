package com.example.platform.billing.api.dto;

import java.time.Instant;

public record CustomPricingResponse(
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
