package com.example.platform.execution.planning;

/**
 * Roadmap #21 typed planning failure algebra (C19).
 *
 * <p>Module-local; describes WHY planning failed. Distinct from #22 runtime
 * failure policy (what execution does after a task fails).
 * ILLEGAL_FUSION is NOT an active V1 surface (fusion DEFERRED) — reserved for
 * future documentation only.
 */
public enum ExecutionPlanningFailureReason {

    /** ExecutionRequirement could not be normalized from #20 declarations. */
    INVALID_EXECUTION_REQUIREMENT,

    /** Logical graph construction produced an invalid graph. */
    INVALID_LOGICAL_GRAPH,

    /** Graph contains a cycle. */
    CYCLE_DETECTED,

    /** A required semantic input is missing. */
    MISSING_SEMANTIC_INPUT,

    /** Requested RenderExtent is inconsistent with the graph/plan. */
    INCONSISTENT_RENDER_EXTENT,

    /** A physical partition violates structural constraints. */
    ILLEGAL_PARTITION,

    /** A structural constraint of the frozen contract is unsatisfied. */
    UNSATISFIED_STRUCTURAL_CONSTRAINT,

    /** A construct is not supported in bounded V1. */
    UNSUPPORTED_V1_PLANNING_CONSTRUCT,

    /** A determinism invariant was violated (planning output not stable). */
    DETERMINISM_INVARIANT_VIOLATION
}
