package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.Map;

public record DiscountPolicyResponse(
        String policyId,
        String policyKey,
        String name,
        String description,
        String discountType,
        double discountValue,
        Map<String, Object> conditions,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt) {
}
