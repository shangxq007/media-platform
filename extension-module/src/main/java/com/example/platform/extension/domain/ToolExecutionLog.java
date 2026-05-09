package com.example.platform.extension.domain;

import java.time.Instant;

/**
 * Audit log entry for a tool execution.
 *
 * <p>Used for observability and debugging. Each execution produces one log entry
 * that captures the request, result, and any error information.</p>
 *
 * @param id            unique log entry identifier
 * @param toolKey       the tool that was executed
 * @param args          the arguments passed (sensitive values should be redacted)
 * @param exitCode      the process exit code
 * @param timedOut      whether the process timed out
 * @param duration      execution duration in milliseconds
 * @param executedAt    timestamp of execution
 * @param initiatedBy   identifier of the user/system that initiated the execution
 */
public record ToolExecutionLog(
        String id,
        String toolKey,
        java.util.List<String> args,
        int exitCode,
        boolean timedOut,
        long duration,
        Instant executedAt,
        String initiatedBy) {
}
