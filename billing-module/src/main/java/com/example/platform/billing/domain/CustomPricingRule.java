package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.Objects;

/** Tenant/workspace-scoped exact pricing override. */
public record CustomPricingRule(
        String ruleId, String tenantId, String workspaceId, String meterKey, long version,
        Money overrideUnitPrice, long discountNumerator, long discountDenominator,
        Instant effectiveFrom, Instant effectiveTo, String status, Instant createdAt) {

    public CustomPricingRule {
        ruleId = required(ruleId, "ruleId");
        tenantId = required(tenantId, "tenantId");
        meterKey = required(meterKey, "meterKey");
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        if (overrideUnitPrice != null && overrideUnitPrice.amountMinor() < 0) {
            throw new IllegalArgumentException("override price must not be negative");
        }
        if (discountNumerator < 0 || discountDenominator <= 0
                || discountNumerator > discountDenominator) {
            throw new IllegalArgumentException("discount must be an exact fraction from zero to one");
        }
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        status = required(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long overridePriceMinor() {
        return overrideUnitPrice == null ? null : overrideUnitPrice.amountMinor();
    }

    public String currencyCode() {
        return overrideUnitPrice == null ? null : overrideUnitPrice.currency();
    }

    public boolean effectiveAt(Instant instant) {
        return "ACTIVE".equals(status) && !instant.isBefore(effectiveFrom)
                && (effectiveTo == null || instant.isBefore(effectiveTo));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
