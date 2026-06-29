package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Policy controlling how the merge planner classifies operations.
 * Planning-only — does not implement conflict resolution.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePlanPolicy {

    /**
     * Conservative: classify any divergent same-path change as conflict.
     */
    CONSERVATIVE,

    /**
     * Allow non-conflicting different paths without issues.
     */
    ALLOW_DIFFERENT_PATHS,

    /**
     * Treat identical same-path changes as SKIPPED_DUPLICATE rather than conflict.
     */
    ALLOW_IDENTICAL_SAME_PATH_CHANGES,

    /**
     * Block on any conflict — return BLOCKED status when conflicts exist.
     */
    BLOCK_ON_ANY_CONFLICT
}
