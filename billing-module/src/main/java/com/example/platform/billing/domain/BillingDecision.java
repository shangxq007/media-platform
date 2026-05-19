package com.example.platform.billing.domain;

import java.util.Map;

public record BillingDecision(
        String decisionId,
        String action,
        String tenantId,
        String userId,
        String pricingModel,
        long estimatedAmountMinor,
        String currencyCode,
        boolean useCredits,
        Map<String, Object> details,
        String status) {

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DENIED = "DENIED";
    public static final String STATUS_PENDING = "PENDING";
}
