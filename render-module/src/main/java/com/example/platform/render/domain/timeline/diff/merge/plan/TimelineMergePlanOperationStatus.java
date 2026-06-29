package com.example.platform.render.domain.timeline.diff.merge.plan;

/**
 * Status of a single operation within a merge plan.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePlanOperationStatus {

    /**
     * Operation is safe — a future merge engine may apply it.
     * Does NOT mean P2V.7 applies it.
     */
    SAFE_TO_APPLY_LATER,

    /**
     * Operation conflicts with another operation — requires manual review.
     */
    CONFLICT_REQUIRES_MANUAL_REVIEW,

    /**
     * Operation uses an unsupported change type.
     */
    UNSUPPORTED,

    /**
     * Operation targets a forbidden/internal path.
     */
    BLOCKED,

    /**
     * Operation is identical to another operation on the same path — skipped to avoid duplication.
     */
    SKIPPED_DUPLICATE
}
