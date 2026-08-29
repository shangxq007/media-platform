package com.example.platform.shared.usage;

import java.util.Objects;

/** Integral base-unit quantity; binary floating point is never canonical. */
public record UsageQuantity(long baseUnits, UsageUnit unit) {

    public UsageQuantity {
        if (baseUnits < 0) {
            throw new IllegalArgumentException("baseUnits must be >= 0");
        }
        Objects.requireNonNull(unit, "unit must not be null");
    }

    public static UsageQuantity fromBaseUnits(long baseUnits, UsageUnit unit) {
        return new UsageQuantity(baseUnits, unit);
    }
}
