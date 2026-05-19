package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.Map;

public record CreateDiscountPolicyRequest(
        String policyKey,
        String name,
        String description,
        String discountType,
        double discountValue,
        Map<String, Object> conditions,
        Instant effectiveFrom,
        Instant effectiveTo) {
}
