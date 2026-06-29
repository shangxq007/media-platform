package com.example.platform.render.domain.timeline.editing;

/**
 * Status of timeline validation.
 * Immutable enum. Internal domain model.
 */
public enum TimelineValidationStatus {
    VALID,
    VALID_WITH_WARNINGS,
    INVALID,
    BLOCKED
}
