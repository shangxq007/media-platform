package com.example.platform.render.domain.timeline.version;

/**
 * Type of timeline commit.
 * Internal domain model.
 */
public enum TimelineCommitType {
    INITIAL,
    EDIT,
    PATCH_APPLICATION,
    ROLLBACK,
    BRANCH_POINT,
    MERGE_PREVIEW,
    MANUAL_MERGE,
    SYSTEM
}
