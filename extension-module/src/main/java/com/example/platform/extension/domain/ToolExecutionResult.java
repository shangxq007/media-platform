package com.example.platform.extension.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of a tool execution.
 *
 * @param exitCode     the process exit code (0 = success)
 * @param stdout       captured standard output
 * @param stderr       captured standard error
 * @param timedOut     whether the process was killed due to timeout
 * @param startTime    when the process was started
 * @param endTime      when the process completed
 * @param duration     total execution duration
 * @param truncated    whether stdout or stderr was truncated due to size limits
 */
public record ToolExecutionResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        Instant startTime,
        Instant endTime,
        Duration duration,
        boolean truncated) {

    /**
     * Returns {@code true} if the tool exited successfully (exit code 0, no timeout).
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * Creates a successful result.
     */
    public static ToolExecutionResult success(int exitCode, String stdout, String stderr,
            Instant startTime, Instant endTime) {
        return new ToolExecutionResult(exitCode, stdout, stderr, false, startTime, endTime,
                Duration.between(startTime, endTime), false);
    }

    /**
     * Creates a failed result (non-zero exit code).
     */
    public static ToolExecutionResult failed(int exitCode, String stdout, String stderr,
            Instant startTime, Instant endTime) {
        return new ToolExecutionResult(exitCode, stdout, stderr, false, startTime, endTime,
                Duration.between(startTime, endTime), false);
    }

    /**
     * Creates a timed-out result.
     */
    public static ToolExecutionResult timedOut(String stdout, String stderr,
            Instant startTime, Instant endTime) {
        return new ToolExecutionResult(-1, stdout, stderr, true, startTime, endTime,
                Duration.between(startTime, endTime), false);
    }
}
