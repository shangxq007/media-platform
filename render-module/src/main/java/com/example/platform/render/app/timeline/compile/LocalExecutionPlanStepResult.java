package com.example.platform.render.app.timeline.compile;

/**
 * Result of executing a single plan step.
 *
 * <p>Internal only — captures step outcome without exposing
 * provider internals, raw commands, or storage paths.</p>
 *
 * @param stepId    the step that was executed
 * @param stepType  the step type name
 * @param status    the step outcome
 * @param message   human-readable summary (no secrets/paths)
 * @param durationMs execution duration in milliseconds
 */
public record LocalExecutionPlanStepResult(
        String stepId,
        String stepType,
        LocalExecutionPlanRunStatus status,
        String message,
        long durationMs) {

    public boolean isSuccess() {
        return status == LocalExecutionPlanRunStatus.SUCCEEDED;
    }

    public boolean isSkipped() {
        return status == LocalExecutionPlanRunStatus.SKIPPED;
    }

    public static LocalExecutionPlanStepResult succeeded(String stepId, String stepType, String message, long durationMs) {
        return new LocalExecutionPlanStepResult(stepId, stepType, LocalExecutionPlanRunStatus.SUCCEEDED, message, durationMs);
    }

    public static LocalExecutionPlanStepResult skipped(String stepId, String stepType, String message) {
        return new LocalExecutionPlanStepResult(stepId, stepType, LocalExecutionPlanRunStatus.SKIPPED, message, 0);
    }

    public static LocalExecutionPlanStepResult failed(String stepId, String stepType, String message, long durationMs) {
        return new LocalExecutionPlanStepResult(stepId, stepType, LocalExecutionPlanRunStatus.FAILED, message, durationMs);
    }

    public static LocalExecutionPlanStepResult blocked(String stepId, String stepType, String message) {
        return new LocalExecutionPlanStepResult(stepId, stepType, LocalExecutionPlanRunStatus.BLOCKED, message, 0);
    }
}
