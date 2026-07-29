package com.example.platform.execution.domain;

/**
 * Defines what happens when an execution step fails.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 */
public enum ExecutionStepFailurePolicy {
    /**
     * Step failure causes the entire plan to fail.
     */
    FAIL_PLAN,
    /**
     * Step failure is allowed if its outputs are marked optional.
     */
    ALLOW_OPTIONAL_OUTPUT_FAILURE,
    /**
     * Step failure requires manual review before proceeding.
     */
    REQUIRE_MANUAL_REVIEW
}
