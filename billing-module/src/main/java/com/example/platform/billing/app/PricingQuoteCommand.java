package com.example.platform.billing.app;

import java.time.Instant;
import java.util.Map;

public record PricingQuoteCommand(
        String tenantId, String workspaceId, String meterKey, long quantityBaseUnits,
        String pricingRuleKey, long pricingRuleVersion, Instant pricedAt,
        Map<String, String> context) {

    public PricingQuoteCommand {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (meterKey == null || meterKey.isBlank()) throw new IllegalArgumentException("meterKey is required");
        if (quantityBaseUnits < 0) throw new IllegalArgumentException("quantity must not be negative");
        if (pricingRuleKey == null || pricingRuleKey.isBlank()) throw new IllegalArgumentException("pricingRuleKey is required");
        if (pricingRuleVersion <= 0) throw new IllegalArgumentException("pricingRuleVersion must be positive");
        if (pricedAt == null) throw new IllegalArgumentException("pricedAt is required");
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
