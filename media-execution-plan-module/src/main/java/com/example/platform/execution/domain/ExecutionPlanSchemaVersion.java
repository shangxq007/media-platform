package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Schema version for the Media Execution Plan model.
 *
 * <p>Version-governed — serialization and validation reject unknown versions.
 */
public record ExecutionPlanSchemaVersion(int value) implements Serializable {

    /**
     * Current schema version for Media Execution Plan V1.
     */
    public static final ExecutionPlanSchemaVersion V1 = new ExecutionPlanSchemaVersion(1);

    public ExecutionPlanSchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("ExecutionPlanSchemaVersion must be >= 1");
        }
    }

    public static ExecutionPlanSchemaVersion of(int value) {
        return new ExecutionPlanSchemaVersion(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
