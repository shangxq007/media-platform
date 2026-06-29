package com.example.platform.render.domain.timeline.version.application;

/**
 * Status of a checkout result.
 * Internal domain model.
 */
public enum TimelineCheckoutResultStatus {
    READY,
    BRANCH_NOT_FOUND,
    REVISION_NOT_FOUND,
    COMMIT_NOT_FOUND,
    INVALID_TARGET,
    BLOCKED,
    FAILED
}
