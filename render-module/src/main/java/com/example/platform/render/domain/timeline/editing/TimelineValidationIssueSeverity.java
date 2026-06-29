package com.example.platform.render.domain.timeline.editing;

/**
 * Severity of a timeline validation issue.
 * Immutable enum. Internal domain model.
 */
public enum TimelineValidationIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}
