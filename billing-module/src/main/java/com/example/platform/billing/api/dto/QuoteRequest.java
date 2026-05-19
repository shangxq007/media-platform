package com.example.platform.billing.api.dto;

public record QuoteRequest(
        String tenantId,
        String userId,
        String meterKey,
        double quantity,
        String unit) {
}
