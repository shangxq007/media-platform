package com.example.platform.render.domain.scenario;

/**
 * Result status of a scenario execution.
 * Immutable enum. Internal domain model.
 */
public enum InternalScenarioResultStatus {
    PASS,
    PASS_WITH_WARNINGS,
    FAIL,
    BLOCKED,
    UNSUPPORTED,
    NOT_RUN
}
