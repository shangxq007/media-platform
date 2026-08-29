package com.example.platform.billing.api.dto;

import java.time.Instant;

public record QuoteRequest(
        String tenantId, String userId, String meterKey, long quantityBaseUnits,
        String unit, String pricingRuleKey, long pricingRuleVersion, Instant pricedAt) {}
