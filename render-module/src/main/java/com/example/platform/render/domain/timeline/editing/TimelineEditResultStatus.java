package com.example.platform.render.domain.timeline.editing;

/**
 * Status of a timeline edit result.
 * Immutable enum. Internal domain model.
 */
public enum TimelineEditResultStatus {
    APPLIED,
    VALIDATION_FAILED,
    NO_OP,
    INVALID_OPERATION,
    BLOCKED,
    FAILED
}
