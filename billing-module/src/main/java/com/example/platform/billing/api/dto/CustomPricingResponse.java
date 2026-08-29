package com.example.platform.billing.api.dto;

import java.time.Instant;

public record CustomPricingResponse(
        String ruleId, String tenantId, String workspaceId, String meterKey, long version,
        Long overridePriceMinor, String currencyCode,
        long discountNumerator, long discountDenominator,
        Instant effectiveFrom, Instant effectiveTo, String status, Instant createdAt) {}
