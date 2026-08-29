package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.Map;

public record CreateDiscountPolicyRequest(
        String tenantId, String policyKey, long ruleVersion,
        String meterKey, String currencyCode,
        String name, String description, String discountType,
        long discountNumerator, long discountDenominator, long flatAmountMinor,
        Map<String, String> conditions, Instant effectiveFrom, Instant effectiveTo) {}
