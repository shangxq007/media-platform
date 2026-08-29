package com.example.platform.render.domain.plan;

/**
 * Severity of a render plan issue.
 * Immutable enum. Internal domain model.
 */
public enum BasicRenderPlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
