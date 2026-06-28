package com.example.platform.render.app.timeline.compile;

/**
 * Status of a local execution plan run or individual step.
 */
public enum LocalExecutionPlanRunStatus {
    NOT_STARTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    FAILED_CLOSED,
    SKIPPED,
    NOT_EXECUTABLE,
    BLOCKED
}
