package com.example.platform.billing.api.dto;

import java.util.Map;

public record PricingPreviewResponse(
        String tenantId, String meterKey, long quantityBaseUnits,
        long estimatedAmountMinor, String currencyCode,
        String pricingRuleId, long pricingRuleVersion, String overrideRuleId,
        Map<String, String> breakdown) {}
