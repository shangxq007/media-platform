package com.example.platform.billing.api.dto;

import java.util.Map;

public record PricingPreviewRequest(
        String tenantId,
        String meterKey,
        double quantity,
        String unit,
        Map<String, String> context) {
}
