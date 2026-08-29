package com.example.platform.billing.api.dto;

import java.time.Instant;

public record CreateCustomPricingRequest(
        String tenantId, String workspaceId, String meterKey,
        Long overridePriceMinor, String currencyCode,
        long discountNumerator, long discountDenominator,
        Instant effectiveFrom, Instant effectiveTo) {}
