package com.example.platform.billing.usage;

import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageUnit;
import java.util.Objects;

/** Explicit immutable and versioned observation-to-billable transformation rule. */
public record MeteringRule(
        String ruleId,
        String version,
        UsageDimension sourceDimension,
        UsageUnit sourceUnit,
        String billableMeter,
        UsageDimension targetDimension,
        UsageUnit targetUnit,
        long numerator,
        long denominator,
        long roundingIncrement,
        MeteringTransformationKind transformationKind,
        String description) {

    public MeteringRule {
        ruleId = requireNonBlank(ruleId, "ruleId");
        version = requireNonBlank(version, "version");
        Objects.requireNonNull(sourceDimension, "sourceDimension must not be null");
        Objects.requireNonNull(sourceUnit, "sourceUnit must not be null");
        UsageUnit.validate(sourceDimension, sourceUnit);
        billableMeter = requireNonBlank(billableMeter, "billableMeter");
        Objects.requireNonNull(targetDimension, "targetDimension must not be null");
        Objects.requireNonNull(targetUnit, "targetUnit must not be null");
        UsageUnit.validate(targetDimension, targetUnit);
        if (numerator < 0) {
            throw new IllegalArgumentException("numerator must be >= 0");
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be > 0");
        }
        if (roundingIncrement <= 0) {
            throw new IllegalArgumentException("roundingIncrement must be > 0");
        }
        Objects.requireNonNull(transformationKind, "transformationKind must not be null");
        description = requireNonBlank(description, "description");
    }

    public long transform(long sourceBaseUnits) {
        if (sourceBaseUnits < 0) {
            throw new IllegalArgumentException("sourceBaseUnits must be >= 0");
        }
        if (transformationKind == MeteringTransformationKind.EXCLUDE) {
            return 0;
        }
        long scaledNumerator = Math.multiplyExact(sourceBaseUnits, numerator);
        long scaled = ceilingDivide(scaledNumerator, denominator);
        if (transformationKind == MeteringTransformationKind.ROUND_UP_INCREMENT) {
            return Math.multiplyExact(
                    ceilingDivide(scaled, roundingIncrement), roundingIncrement);
        }
        return scaled;
    }

    public String transformationDetails() {
        return "kind=" + transformationKind
                + ";numerator=" + numerator
                + ";denominator=" + denominator
                + ";roundingIncrement=" + roundingIncrement
                + ";source=" + sourceDimension + "/" + sourceUnit
                + ";target=" + billableMeter + "/" + targetDimension + "/" + targetUnit;
    }

    private static long ceilingDivide(long value, long divisor) {
        if (value == 0) {
            return 0;
        }
        return Math.addExact(value, divisor - 1) / divisor;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
