package com.example.platform.execution.domain;

/**
 * Classifies the nature of a dependency between execution steps.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 */
public enum ExecutionDependencyType {
    /**
     * Data dependency — fromStep produces output consumed by toStep.
     */
    DATA,
    /**
     * Control dependency — fromStep must complete before toStep starts, but no data flows.
     */
    CONTROL,
    /**
     * Validation dependency — fromStep validates preconditions for toStep.
     */
    VALIDATION
}
