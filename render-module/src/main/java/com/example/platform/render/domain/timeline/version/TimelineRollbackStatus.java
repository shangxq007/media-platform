package com.example.platform.render.domain.timeline.version;

/**
 * Status of a rollback plan.
 * Internal domain model.
 */
public enum TimelineRollbackStatus {
    READY,
    INVALID_TARGET,
    TARGET_NOT_ANCESTOR,
    NO_OP,
    BLOCKED
}
