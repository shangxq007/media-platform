package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 canonical plan schema version (frozen ledger REUSE_AS_CANONICAL).
 *
 * <p>FROZEN canonical semantics (Decision Recovery): single integer value,
 * V1 = new ExecutionPlanSchemaVersion(1), value &gt;= 1. Distinct from the
 * plan formatVersion concept in the #21 model — never conflated.
 */
public record ExecutionPlanSchemaVersion(int value) {

    public ExecutionPlanSchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("ExecutionPlanSchemaVersion must be >= 1");
        }
    }

    public String canonical() {
        return String.valueOf(value);
    }
}
