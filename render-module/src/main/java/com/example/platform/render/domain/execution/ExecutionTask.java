package com.example.platform.render.domain.execution;

import java.util.List;

/**
 * A unit of work within an ExecutionJob.
 */
public record ExecutionTask(
        String taskId,
        BackendExecutionSpec backendSpec,
        List<String> dependencies,
        String retryPolicy,
        ExecutionStatus status,
        int attempt,
        String workerId) {

    public static ExecutionTask of(BackendExecutionSpec spec) {
        return new ExecutionTask("task-" + System.currentTimeMillis(),
                spec, List.of(), "NO_RETRY", ExecutionStatus.CREATED, 0, null);
    }
}
