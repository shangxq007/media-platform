package com.example.platform.billing.usage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical usage units (frozen vocabulary).
 *
 * <p>Each unit is only legal for a specific set of {@link UsageDimension}s. An illegal
 * dimension/unit pairing is rejected with {@link IllegalArgumentException} by
 * {@link #validate(UsageDimension, UsageUnit)}.</p>
 */
public enum UsageUnit {

    COUNT(UsageDimension.REQUEST),
    MILLISECONDS(UsageDimension.DURATION),
    SECONDS(UsageDimension.DURATION),
    BYTE(UsageDimension.BYTE_STORED,
            UsageDimension.BYTE_READ,
            UsageDimension.BYTE_WRITTEN,
            UsageDimension.BYTE_EGRESS,
            UsageDimension.DELIVERY_BYTE),
    TOKEN(UsageDimension.TOKEN_INPUT, UsageDimension.TOKEN_OUTPUT);

    private static final Map<UsageUnit, Set<UsageDimension>> DIMENSIONS_BY_UNIT;

    static {
        Map<UsageUnit, Set<UsageDimension>> map = new EnumMap<>(UsageUnit.class);
        for (UsageUnit unit : values()) {
            map.put(unit, Collections.unmodifiableSet(unit.dimensions));
        }
        DIMENSIONS_BY_UNIT = Collections.unmodifiableMap(map);
    }

    private final Set<UsageDimension> dimensions;

    UsageUnit(UsageDimension... dimensions) {
        this.dimensions = Set.of(dimensions);
    }

    /** The dimensions for which this unit is legal. */
    public Set<UsageDimension> compatibleDimensions() {
        return dimensions;
    }

    /** True if this unit is legal for the given dimension. */
    public boolean isCompatibleWith(UsageDimension dimension) {
        return dimensions.contains(dimension);
    }

    /**
     * Validates that the given unit is legal for the given dimension.
     *
     * @param dimension the usage dimension
     * @param unit      the unit to validate
     * @throws IllegalArgumentException if the pairing is illegal
     * @throws NullPointerException     if either argument is null
     */
    public static void validate(UsageDimension dimension, UsageUnit unit) {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        if (!unit.isCompatibleWith(dimension)) {
            throw new IllegalArgumentException(
                    "Unit " + unit + " is not valid for dimension " + dimension);
        }
    }
}
