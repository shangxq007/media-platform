package com.example.platform.billing.api.dto;

public record QuoteResponse(
        String tenantId,
        String meterKey,
        double quantity,
        String unit,
        long estimatedAmountMinor,
        String currencyCode,
        String pricingModel) {
}
