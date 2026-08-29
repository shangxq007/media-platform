package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.Map;

public record DiscountPolicyResponse(
        String policyId, String tenantId, String policyKey, long ruleVersion,
        String meterKey, String currencyCode,
        String name, String description, String discountType,
        long discountNumerator, long discountDenominator, long flatAmountMinor,
        Map<String, String> conditions, String status,
        Instant effectiveFrom, Instant effectiveTo, Instant createdAt) {}
