package com.example.platform.render.domain.timeline.version.application;

/**
 * Status of a branch switch result.
 * Internal domain model.
 */
public enum TimelineBranchSwitchResultStatus {
    READY,
    SOURCE_BRANCH_NOT_FOUND,
    TARGET_BRANCH_NOT_FOUND,
    UNSAVED_CHANGES_REQUIRE_DECISION,
    INVALID_REQUEST,
    BLOCKED,
    FAILED
}
