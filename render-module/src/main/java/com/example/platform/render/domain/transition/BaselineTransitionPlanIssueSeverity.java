package com.example.platform.render.domain.transition;

/**
 * Severity of a baseline transition plan issue.
 * Immutable enum. Internal domain model.
 */
public enum BaselineTransitionPlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
