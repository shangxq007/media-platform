package com.example.platform.billing.api.dto;

import java.util.Map;

public record PricingPreviewResponse(
        String tenantId,
        String meterKey,
        double quantity,
        long estimatedAmountMinor,
        String currencyCode,
        Map<String, Object> breakdown) {
}
