package com.example.platform.render.app.timeline.compile;

import java.util.List;

/**
 * Result of a local execution plan run.
 *
 * <p>Internal only — captures overall status, per-step results,
 * and the output product ID if successful.</p>
 *
 * @param status         overall run status
 * @param planId         the plan that was executed
 * @param stepResults    per-step results
 * @param message        human-readable summary
 * @param outputProductId the output product ID (null if not produced)
 */
public record LocalExecutionPlanRunResult(
        LocalExecutionPlanRunStatus status,
        String planId,
        String localExecutionRunId,
        List<LocalExecutionPlanStepResult> stepResults,
        String message,
        String outputProductId) {

    public boolean isSuccess() {
        return status == LocalExecutionPlanRunStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == LocalExecutionPlanRunStatus.FAILED
                || status == LocalExecutionPlanRunStatus.FAILED_CLOSED;
    }

    public static LocalExecutionPlanRunResult failedClosed(String message) {
        return new LocalExecutionPlanRunResult(
                LocalExecutionPlanRunStatus.FAILED_CLOSED, null, null, List.of(), message, null);
    }

    public static LocalExecutionPlanRunResult notExecutable(String message) {
        return new LocalExecutionPlanRunResult(
                LocalExecutionPlanRunStatus.NOT_EXECUTABLE, null, null, List.of(), message, null);
    }
}
