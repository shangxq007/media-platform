package com.example.platform.execution.planning;

/**
 * Roadmap #21 typed planning failure algebra (C19) — ACTIVE V1 classes.
 *
 * <p>Describes WHY planning failed. Distinct from #22 runtime failure policy
 * (what execution does after a task fails). ILLEGAL_FUSION is NOT an active
 * V1 surface (fusion DEFERRED).
 *
 * <p>FAOF-1 formalization hook: each reason maps to a language-neutral
 * invariant identifier ({@link #lawId()}).
 */
public enum ExecutionPlanningFailureReason {

    /** ExecutionRequirement could not be normalized from #20 declarations. */
    INVALID_EXECUTION_REQUIREMENT("law:req-normalization-injective"),

    /** Logical graph construction produced an invalid graph. */
    INVALID_LOGICAL_GRAPH("law:logical-graph-valid"),

    /** Graph contains a cycle. */
    CYCLE_DETECTED("law:dag-acyclic"),

    /** A required semantic input is missing. */
    MISSING_SEMANTIC_INPUT("law:inputs-closed"),

    /** Requested RenderExtent is inconsistent with the graph/plan. */
    INCONSISTENT_RENDER_EXTENT("law:extent-single-authority"),

    /** A physical partition violates structural constraints. */
    ILLEGAL_PARTITION("law:partition-1-to-1"),

    /** A structural constraint of the frozen contract is unsatisfied. */
    UNSATISFIED_STRUCTURAL_CONSTRAINT("law:structural-constraints"),

    /** A construct is not supported in bounded V1. */
    UNSUPPORTED_V1_PLANNING_CONSTRUCT("law:v1-surface-bounded"),

    /** A determinism invariant was violated (planning output not stable). */
    DETERMINISM_INVARIANT_VIOLATION("law:planning-deterministic");

    private final String lawId;

    ExecutionPlanningFailureReason(String lawId) {
        this.lawId = lawId;
    }

    /** Language-neutral invariant identifier for future formal verification. */
    public String lawId() {
        return lawId;
    }
}
