package com.example.platform.render.domain.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Execution Job — common contract shared by all execution environments.
 * Immutable. Platform-owned lifecycle semantics.
 */
public record ExecutionJob(
        String jobId,
        String environmentId,
        String backendId,
        String backendType,
        int priority,
        Map<String, Object> resourceRequirements,
        Map<String, String> schedulingHints,
        List<ExecutionTask> tasks,
        ExecutionStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String failureReason) {

    public static ExecutionJob of(String envId, String backendId,
                                    String backendType, List<ExecutionTask> tasks) {
        return new ExecutionJob("job-" + System.currentTimeMillis(),
                envId, backendId, backendType, 50,
                Map.of("cpu", 1, "memoryMb", 1024),
                Map.of(), tasks, ExecutionStatus.CREATED,
                Instant.now(), null, null, null);
    }

    public ExecutionJob withStatus(ExecutionStatus newStatus) {
        return new ExecutionJob(jobId, environmentId, backendId, backendType,
                priority, resourceRequirements, schedulingHints, tasks,
                newStatus, createdAt,
                newStatus == ExecutionStatus.RUNNING ? Instant.now() : startedAt,
                newStatus == ExecutionStatus.COMPLETED || newStatus == ExecutionStatus.FAILED
                        ? Instant.now() : finishedAt,
                newStatus == ExecutionStatus.FAILED ? failureReason : null);
    }
}
