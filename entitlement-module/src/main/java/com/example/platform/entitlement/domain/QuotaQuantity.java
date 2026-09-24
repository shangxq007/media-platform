package com.example.platform.entitlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical quota quantity policy: NUMERIC(38,18), scale at most 18, exact comparisons. */
public final class QuotaQuantity {
    public static final int SCALE = 18;
    public static final int PRECISION = 38;
    public static final RoundingMode ROUNDING = RoundingMode.UNNECESSARY;
    private QuotaQuantity() {}

    public static BigDecimal exact(BigDecimal value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        if (value.scale() > SCALE || value.precision() > PRECISION)
            throw new IllegalArgumentException(field + " exceeds NUMERIC(" + PRECISION + "," + SCALE + ")");
        return value;
    }
}
