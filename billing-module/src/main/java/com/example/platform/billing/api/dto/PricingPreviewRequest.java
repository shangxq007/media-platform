package com.example.platform.billing.api.dto;

import java.time.Instant;
import java.util.Map;

public record PricingPreviewRequest(
        String tenantId, String workspaceId, String meterKey, long quantityBaseUnits,
        String unit, String pricingRuleKey, long pricingRuleVersion,
        Instant pricedAt, Map<String, String> context) {}
