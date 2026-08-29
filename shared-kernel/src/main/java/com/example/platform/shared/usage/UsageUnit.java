package com.example.platform.shared.usage;

import java.util.Set;

/** Explicit base units accepted by runtime observation and billing metering. */
public enum UsageUnit {
    COUNT(UsageDimension.REQUEST),
    MILLISECONDS(UsageDimension.DURATION),
    SECONDS(UsageDimension.DURATION),
    BYTE(
            UsageDimension.BYTE_STORED,
            UsageDimension.BYTE_READ,
            UsageDimension.BYTE_WRITTEN,
            UsageDimension.BYTE_EGRESS,
            UsageDimension.DELIVERY_BYTE),
    TOKEN(UsageDimension.TOKEN_INPUT, UsageDimension.TOKEN_OUTPUT);

    private final Set<UsageDimension> compatibleDimensions;

    UsageUnit(UsageDimension... compatibleDimensions) {
        this.compatibleDimensions = Set.of(compatibleDimensions);
    }

    public boolean isCompatibleWith(UsageDimension dimension) {
        return compatibleDimensions.contains(dimension);
    }

    public static void validate(UsageDimension dimension, UsageUnit unit) {
        if (dimension == null || unit == null) {
            throw new NullPointerException("dimension and unit must not be null");
        }
        if (!unit.isCompatibleWith(dimension)) {
            throw new IllegalArgumentException(
                    "Unit " + unit + " is not valid for dimension " + dimension);
        }
    }
}
