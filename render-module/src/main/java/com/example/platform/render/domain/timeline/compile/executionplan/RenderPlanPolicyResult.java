package com.example.platform.render.domain.timeline.compile.executionplan;

import java.util.List;

/**
 * Result of RenderPlanPolicyGuard evaluation.
 *
 * <p>Internal only — captures the overall verdict and any violations found.</p>
 *
 * @param status      policy guard verdict
 * @param violations  list of violations found (empty if valid)
 * @param explanation human-readable summary
 */
public record RenderPlanPolicyResult(
        RenderPlanPolicyStatus status,
        List<RenderPlanPolicyViolation> violations,
        String explanation) {

    /**
     * Returns true if the plan passed all policy checks.
     */
    public boolean isValid() {
        return status == RenderPlanPolicyStatus.VALID_FOR_DRY_RUN;
    }

    /**
     * Returns true if the plan has violations.
     */
    public boolean hasViolations() {
        return violations != null && !violations.isEmpty();
    }

    /**
     * Returns true if the plan was rejected (not executable).
     */
    public boolean isRejected() {
        return status == RenderPlanPolicyStatus.NOT_EXECUTABLE
                || status == RenderPlanPolicyStatus.FAILED_CLOSED;
    }

    /**
     * Creates a valid result with no violations.
     */
    public static RenderPlanPolicyResult valid(String explanation) {
        return new RenderPlanPolicyResult(
                RenderPlanPolicyStatus.VALID_FOR_DRY_RUN, List.of(), explanation);
    }

    /**
     * Creates a not-executable result with violations.
     */
    public static RenderPlanPolicyResult notExecutable(
            List<RenderPlanPolicyViolation> violations, String explanation) {
        return new RenderPlanPolicyResult(
                RenderPlanPolicyStatus.NOT_EXECUTABLE, violations, explanation);
    }

    /**
     * Creates a failed-closed result with violations.
     */
    public static RenderPlanPolicyResult failedClosed(
            List<RenderPlanPolicyViolation> violations, String explanation) {
        return new RenderPlanPolicyResult(
                RenderPlanPolicyStatus.FAILED_CLOSED, violations, explanation);
    }
}
