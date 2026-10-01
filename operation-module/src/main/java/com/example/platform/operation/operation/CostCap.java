package com.example.platform.operation.operation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Provider-neutral cost/quota cap (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b).
 *
 * <p>Amount is a {@link BigDecimal} (never {@code double}), matching the
 * platform convention used by composition cost estimates and billing
 * observations. It is a typed cap, never free-form metadata.</p>
 */
public record CostCap(BigDecimal units, String unit) {

    public CostCap {
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(unit, "unit");
        if (units.signum() < 0) {
            throw new IllegalArgumentException("cost cap units must be >= 0: " + units);
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("cost cap unit must not be blank");
        }
    }
}
