package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;
import java.util.List;
import java.util.Map;

/**
 * Execution request for an execution backend.
 * Domain-agnostic — knows nothing about assets, marketplaces, or reviews.
 */
public record ExecutionRequest(
        String jobId,
        String taskId,
        TaskCapability taskCapability,
        String workingDirectory,
        Map<String, String> environment,
        List<String> arguments,
        int timeoutSeconds,
        String tenantId,
        String projectId,
        Map<String, String> payload) {

    public static ExecutionRequest of(String jobId, String taskId, TaskCapability cap,
                                         List<String> args, int timeout) {
        return new ExecutionRequest(jobId, taskId, cap, null, Map.of(), args,
                timeout, null, null, Map.of());
    }
}
