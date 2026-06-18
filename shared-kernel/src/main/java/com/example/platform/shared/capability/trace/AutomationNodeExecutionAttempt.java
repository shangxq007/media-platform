package com.example.platform.shared.capability.trace;

import java.time.Instant;

/**
 * Represents a single attempt of a node execution.
 *
 * <p><strong>Contract only:</strong> This defines the attempt shape.
 * Persistence is not implemented.</p>
 */
public record AutomationNodeExecutionAttempt(
    int attemptNumber,
    Instant startedAt,
    Instant completedAt,
    AutomationNodeExecutionTraceStatus status,
    String errorCode,
    String message,
    boolean retryable,
    String logsRef
) {
    public AutomationNodeExecutionAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
    }

    /**
     * Create a succeeded attempt.
     */
    public static AutomationNodeExecutionAttempt succeeded(int attemptNumber, Instant startedAt, Instant completedAt) {
        return new AutomationNodeExecutionAttempt(
            attemptNumber,
            startedAt,
            completedAt,
            AutomationNodeExecutionTraceStatus.SUCCEEDED,
            null,
            null,
            false,
            null
        );
    }

    /**
     * Create a failed attempt.
     */
    public static AutomationNodeExecutionAttempt failed(int attemptNumber, Instant startedAt, Instant completedAt, String errorCode, String message, boolean retryable) {
        return new AutomationNodeExecutionAttempt(
            attemptNumber,
            startedAt,
            completedAt,
            AutomationNodeExecutionTraceStatus.FAILED,
            errorCode,
            message,
            retryable,
            null
        );
    }
}
