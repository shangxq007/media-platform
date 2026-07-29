package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable digest of an Execution Plan.
 *
 * <p>Computed from canonical representation — same semantic plan yields same digest,
 * independent of insertion order, HashMap iteration, locale, timezone, or machine architecture.
 */
public record ExecutionPlanDigest(String value) implements Serializable {

    public ExecutionPlanDigest {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionPlanDigest must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
