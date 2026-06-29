package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Status of a non-conflicting merge plan.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePlanStatus {

    /**
     * Plan is ready — all operations are safe to apply later.
     */
    READY,

    /**
     * Plan requires manual review — some operations have conflicts.
     */
    MANUAL_REVIEW_REQUIRED,

    /**
     * Plan is blocked — forbidden paths or blocked operations detected.
     */
    BLOCKED,

    /**
     * Plan is unsupported — unsupported change types detected.
     */
    UNSUPPORTED,

    /**
     * Plan input is invalid — missing base/ours/theirs or bad request.
     */
    INVALID_INPUT,

    /**
     * Plan generation failed — internal error.
     */
    FAILED
}
