package com.example.platform.billing.usage;

import java.util.Objects;

/**
 * Typed canonical usage quantity.
 *
 * <p>Represented as a {@code long} base-unit count plus a {@link UsageUnit}. There is
 * NO double field — the legacy {@code usage_record.quantity double} column is not the
 * canonical authority.</p>
 *
 * @param baseUnits the quantity in the unit's base units (must be &gt;= 0)
 * @param unit      the canonical unit
 */
public record UsageQuantity(long baseUnits, UsageUnit unit) {

    public UsageQuantity {
        if (baseUnits < 0) {
            throw new IllegalArgumentException("baseUnits must be >= 0, was: " + baseUnits);
        }
        Objects.requireNonNull(unit, "unit must not be null");
    }

    /** Canonical factory. {@code baseUnits} must be &gt;= 0. */
    public static UsageQuantity fromBaseUnits(long baseUnits, UsageUnit unit) {
        return new UsageQuantity(baseUnits, unit);
    }
}
