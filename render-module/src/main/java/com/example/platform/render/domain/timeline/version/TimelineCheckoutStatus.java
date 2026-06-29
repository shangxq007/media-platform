package com.example.platform.render.domain.timeline.version;

/**
 * Status of a checkout plan.
 * Internal domain model.
 */
public enum TimelineCheckoutStatus {
    READY,
    INVALID_TARGET,
    BRANCH_NOT_FOUND,
    REVISION_NOT_FOUND,
    BLOCKED
}
