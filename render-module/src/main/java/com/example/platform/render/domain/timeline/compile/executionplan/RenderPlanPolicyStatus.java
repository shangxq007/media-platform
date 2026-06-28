package com.example.platform.render.domain.timeline.compile.executionplan;

/**
 * Verdict of the RenderPlanPolicyGuard.
 */
public enum RenderPlanPolicyStatus {

    /** Plan is valid for dry-run planning (may not be executable). */
    VALID_FOR_DRY_RUN,

    /** Plan is not executable due to policy violations. */
    NOT_EXECUTABLE,

    /** Plan was rejected by fail-closed safety constraints. */
    FAILED_CLOSED
}
