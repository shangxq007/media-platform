package com.example.platform.render.domain;

/**
 * Status of a single {@link RenderStep} within a {@link RenderPlan}.
 *
 * <p>Status transitions:</p>
 * <pre>
 * PENDING → RUNNING → COMPLETED
 * PENDING → RUNNING → FAILED
 * PENDING → RUNNING → CANCELLED
 * PENDING → SKIPPED
 * FAILED → PENDING (on retry)
 * </pre>
 */
public enum RenderStepStatus {

    /** Step is queued and waiting to execute. */
    PENDING,

    /** Step is currently executing. */
    RUNNING,

    /** Step completed successfully. */
    COMPLETED,

    /** Step failed with an error. */
    FAILED,

    /** Step was cancelled (e.g., due to job cancellation). */
    CANCELLED,

    /** Step was skipped (e.g., conditional step). */
    SKIPPED
}
