package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, versioned commercial pricing rule. */
public record PricingRule(
        String ruleId, String tenantId, String ruleKey, long version,
        String name, String description, PricingModel pricingModel, String meterKey,
        Money unitPrice, List<PricingTier> tiers, String status,
        Instant effectiveFrom, Instant effectiveTo, Instant createdAt, Instant updatedAt) {

    public PricingRule {
        ruleId = required(ruleId, "ruleId");
        tenantId = required(tenantId, "tenantId");
        ruleKey = required(ruleKey, "ruleKey");
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        name = required(name, "name");
        description = description == null ? "" : description;
        pricingModel = Objects.requireNonNull(pricingModel, "pricingModel");
        meterKey = required(meterKey, "meterKey");
        unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.amountMinor() < 0) throw new IllegalArgumentException("unit price must not be negative");
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
        status = required(status, "status");
        effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Compatibility constructor for callers migrated before durable pricing versioning. */
    public PricingRule(String ruleId, String ruleKey, String name, String description,
                       PricingModel pricingModel, String meterKey, long unitPriceMinor,
                       String currencyCode, List<PricingTier> tiers, String status,
                       Instant effectiveFrom, Instant effectiveTo, Instant createdAt,
                       Instant updatedAt) {
        this(ruleId, "GLOBAL", ruleKey, 1, name, description, pricingModel, meterKey,
                new Money(unitPriceMinor, currencyCode), tiers, status,
                effectiveFrom == null ? createdAt : effectiveFrom, effectiveTo, createdAt, updatedAt);
    }

    public long unitPriceMinor() { return unitPrice.amountMinor(); }
    public String currencyCode() { return unitPrice.currency(); }

    public boolean effectiveAt(Instant instant) {
        return "ACTIVE".equals(status) && !instant.isBefore(effectiveFrom)
                && (effectiveTo == null || instant.isBefore(effectiveTo));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
