package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.Map;

/** Immutable versioned exact discount rule scoped to a tenant and meter. */
public record DiscountPolicy(
        String policyId, String tenantId, String policyKey, long version,
        String meterKey, String currencyCode, String name, String description,
        String discountType, long discountNumerator, long discountDenominator,
        long flatAmountMinor, Map<String, String> conditions, String status,
        Instant effectiveFrom, Instant effectiveTo, Instant createdAt) {

    public DiscountPolicy {
        if (policyId == null || policyId.isBlank() || tenantId == null || tenantId.isBlank()
                || policyKey == null || policyKey.isBlank() || meterKey == null || meterKey.isBlank()) {
            throw new IllegalArgumentException("discount identity, tenant, key, and meter are required");
        }
        if (version <= 0) throw new IllegalArgumentException("discount version must be positive");
        currencyCode = new Money(0, currencyCode).currency();
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
        if (discountDenominator <= 0 || discountNumerator < 0
                || discountNumerator > discountDenominator || flatAmountMinor < 0) {
            throw new IllegalArgumentException("invalid exact discount");
        }
    }

    public boolean effectiveAt(Instant instant) {
        return "ACTIVE".equals(status) && !instant.isBefore(effectiveFrom)
                && (effectiveTo == null || instant.isBefore(effectiveTo));
    }
}
