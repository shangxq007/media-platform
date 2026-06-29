package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Source of a merge plan operation.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePlanOperationSource {

    /**
     * Operation originates from the ours diff.
     */
    OURS,

    /**
     * Operation originates from the theirs diff.
     */
    THEIRS,

    /**
     * Operation is identical on both sides.
     */
    BOTH_IDENTICAL,

    /**
     * Operation is system-generated (e.g., metadata).
     */
    SYSTEM
}
