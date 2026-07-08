package com.example.platform.outbox.coordination;

import java.util.List;
import java.util.Map;

/**
 * Result from an execution backend. Domain-agnostic.
 */
public record ExecutionResult(
        boolean success,
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        List<String> outputFiles,
        Map<String, Object> artifacts,
        Map<String, String> metadata,
        String errorCode,
        String errorMessage) {

    public static ExecutionResult success(int exitCode, String stdout, String stderr, long durationMs) {
        return new ExecutionResult(true, exitCode, stdout, stderr, durationMs,
                List.of(), Map.of(), Map.of(), null, null);
    }

    public static ExecutionResult failure(int exitCode, String stderr, long durationMs,
                                            String errorCode, String errorMessage) {
        return new ExecutionResult(false, exitCode, "", stderr, durationMs,
                List.of(), Map.of(), Map.of(), errorCode, errorMessage);
    }
}
