package com.example.platform.render.domain.timeline.version;

/**
 * Status of a branch switch plan.
 * Internal domain model.
 */
public enum TimelineBranchSwitchStatus {
    READY,
    SOURCE_BRANCH_NOT_FOUND,
    TARGET_BRANCH_NOT_FOUND,
    UNSAVED_CHANGES_REQUIRE_DECISION,
    BLOCKED
}
