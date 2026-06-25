package com.example.platform.outbox.app;

import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.outbox.domain.PlatformJob;
import com.example.platform.outbox.domain.PlatformTask;

/**
 * Execution context passed to a TaskHandler when a task is dispatched.
 */
public record TaskExecutionContext(
        String jobId,
        String taskId,
        TaskCapability capability,
        PlatformJob job,
        PlatformTask task,
        String payload) {

    public static TaskExecutionContext of(PlatformJob job, PlatformTask task) {
        return new TaskExecutionContext(job.id(), task.id(), task.capability(),
                job, task, job.payloadJson());
    }
}
