package com.example.platform.render.domain.artifact;

/**
 * Status of an Artifact DAG evaluation.
 *
 * <p>Used to report the outcome of Artifact DAG evaluation without blocking render.
 * Combined with {@link ArtifactDagMode} to control behavior.</p>
 */
public enum ArtifactDagEvaluationStatus {

    /**
     * Artifact DAG was not computed because mode is DISABLED.
     */
    SKIPPED_DISABLED,

    /**
     * Artifact DAG was computed in DRY_RUN mode for analysis only.
     * Result must not affect render decisions.
     */
    DRY_RUN_COMPLETED,

    /**
     * Artifact DAG was computed in EXPERIMENTAL mode.
     * Result is internal-only and must not affect default paths.
     */
    EXPERIMENTAL_COMPLETED,

    /**
     * Artifact DAG computation was not performed.
     */
    NOT_COMPUTED,

    /**
     * Artifact DAG computation failed but failure is non-blocking.
     * Render continues without Artifact DAG analysis.
     */
    FAILED_NON_BLOCKING;

    /**
     * Returns true if this status indicates the DAG was computed.
     */
    public boolean wasComputed() {
        return this == DRY_RUN_COMPLETED || this == EXPERIMENTAL_COMPLETED;
    }

    /**
     * Returns true if this status is a non-blocking failure.
     */
    public boolean isNonBlockingFailure() {
        return this == FAILED_NON_BLOCKING;
    }
}
