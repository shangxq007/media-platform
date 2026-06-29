package com.example.platform.render.domain.timeline.version.application;

/**
 * Status of a rollback result.
 * Internal domain model.
 */
public enum TimelineRollbackResultStatus {
    READY,
    NO_OP,
    TARGET_NOT_FOUND,
    TARGET_NOT_ANCESTOR,
    INVALID_REQUEST,
    BLOCKED,
    FAILED
}
