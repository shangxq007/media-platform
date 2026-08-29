package com.example.platform.billing.api.dto;

public record QuoteResponse(
        String tenantId, String meterKey, long quantityBaseUnits, String unit,
        long estimatedAmountMinor, String currencyCode, String pricingModel,
        String pricingRuleId, long pricingRuleVersion) {}
