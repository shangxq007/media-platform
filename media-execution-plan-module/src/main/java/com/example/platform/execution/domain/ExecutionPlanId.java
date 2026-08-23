package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Business identity of an Execution Plan.
 *
 * <p>Immutable strong type — the plan identity is independent of plan content.
 * Two plans with identical DAG structure but different plan IDs are distinct plans.
 */
public record ExecutionPlanId(String value) implements Serializable {

    public ExecutionPlanId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionPlanId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
