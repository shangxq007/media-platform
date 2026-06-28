package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Status of an execution step in the plan.
 *
 * <p>v0 status is PENDING for all planned steps — no execution occurs.</p>
 */
public enum RenderExecutionStepStatus {

    /** Step is planned and pending. */
    PENDING,

    /** Step is ready for future execution. */
    READY,

    /** Step was skipped (e.g., optional capability not needed). */
    SKIPPED,

    /** Step failed validation or policy guard. */
    FAILED,

    /** Step is blocked by upstream dependency failure. */
    BLOCKED
}
