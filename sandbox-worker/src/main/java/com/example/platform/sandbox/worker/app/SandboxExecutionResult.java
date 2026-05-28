package com.example.platform.sandbox.worker.app;

/**
 * Result of a sandbox code execution.
 */
public record SandboxExecutionResult(
    Status status,
    String stdout,
    String stderr,
    int exitCode,
    long durationMs,
    boolean truncated,
    String errorCode,
    String message
) {
    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
        DENIED,
        ERROR
    }

    public static SandboxExecutionResult success(String stdout, boolean truncated) {
        return new SandboxExecutionResult(Status.SUCCESS, stdout, "", 0, 0, truncated, "", "");
    }

    public static SandboxExecutionResult failed(String stderr, int exitCode) {
        return new SandboxExecutionResult(Status.FAILED, "", stderr, exitCode, 0, false, "", "");
    }

    public static SandboxExecutionResult timeout(long timeoutMs) {
        return new SandboxExecutionResult(Status.TIMEOUT, "", "", -1, timeoutMs, false,
                "TIMEOUT", "Execution timed out after " + timeoutMs + "ms");
    }

    public static SandboxExecutionResult denied(String message) {
        return new SandboxExecutionResult(Status.DENIED, "", "", -1, 0, false, "DENIED", message);
    }

    public static SandboxExecutionResult error(String message) {
        return new SandboxExecutionResult(Status.ERROR, "", "", -1, 0, false, "ERROR", message);
    }
}
