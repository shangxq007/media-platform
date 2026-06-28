package com.example.platform.render.domain.timeline.compile.executionplan;

import java.util.List;

/**
 * Summary of a render execution plan for logging and diagnostics.
 *
 * <p>Internal only — captures high-level plan state without exposing
 * provider internals, storage paths, or commands.</p>
 *
 * @param planId            plan identifier
 * @param bindingPlanId     source binding plan identifier
 * @param timelineId        source timeline identifier
 * @param mode              execution mode
 * @param environmentTarget execution environment target
 * @param totalSteps        total number of steps
 * @param pendingSteps      steps in PENDING status
 * @param failedSteps       steps in FAILED status
 * @param blockedSteps      steps in BLOCKED status
 * @param executionReady    whether plan has all steps execution-ready (false in v0)
 * @param boundProviders    names of bound providers (no internals)
 * @param failureReasons    plan-level failure reasons (empty if valid)
 */
public record RenderExecutionPlanSummary(
        String planId,
        String bindingPlanId,
        String timelineId,
        String mode,
        ExecutionEnvironmentTarget environmentTarget,
        int totalSteps,
        int pendingSteps,
        int failedSteps,
        int blockedSteps,
        boolean executionReady,
        List<String> boundProviders,
        List<RenderExecutionPlanFailureReason> failureReasons) {

    /**
     * Returns a human-readable summary string.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("RenderExecutionPlan[").append(planId).append("] ");
        sb.append("mode=").append(mode);
        sb.append(" target=").append(environmentTarget);
        sb.append(" steps=").append(totalSteps);
        sb.append(" pending=").append(pendingSteps);
        sb.append(" failed=").append(failedSteps);
        sb.append(" blocked=").append(blockedSteps);
        sb.append(" execReady=").append(executionReady);
        if (!boundProviders.isEmpty()) {
            sb.append(" providers=").append(boundProviders);
        }
        return sb.toString();
    }
}
