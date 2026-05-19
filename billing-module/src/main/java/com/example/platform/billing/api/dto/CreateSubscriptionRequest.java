package com.example.platform.billing.api.dto;

public record CreateSubscriptionRequest(
        String tenantId,
        String userId,
        String planKey,
        int periodDays) {
}
