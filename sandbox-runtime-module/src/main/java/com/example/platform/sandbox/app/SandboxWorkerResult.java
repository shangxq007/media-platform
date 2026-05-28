package com.example.platform.sandbox.app;

/**
 * Result returned by an external sandbox worker.
 */
public record SandboxWorkerResult(
    Status status,
    String stdout,
    String stderr,
    int exitCode,
    long durationMs,
    boolean truncated,
    String errorCode,
    String message,
    String workerId,
    String runtime
) {
    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT,
        DENIED,
        ERROR
    }

    public SandboxWorkerResult {
        if (status == null) status = Status.ERROR;
        if (stdout == null) stdout = "";
        if (stderr == null) stderr = "";
        if (errorCode == null) errorCode = "";
        if (message == null) message = "";
        if (workerId == null) workerId = "";
        if (runtime == null) runtime = "";
    }

    public static SandboxWorkerResult success(String stdout, long durationMs) {
        return new SandboxWorkerResult(Status.SUCCESS, stdout, "", 0, durationMs,
                false, "", "", "", "");
    }

    public static SandboxWorkerResult error(String message) {
        return new SandboxWorkerResult(Status.ERROR, "", "", -1, 0,
                false, "WORKER_ERROR", message, "", "");
    }

    public static SandboxWorkerResult timeout(long timeoutMs) {
        return new SandboxWorkerResult(Status.TIMEOUT, "", "", -1, timeoutMs,
                false, "TIMEOUT", "Execution timed out after " + timeoutMs + "ms", "", "");
    }

    public static SandboxWorkerResult denied(String message) {
        return new SandboxWorkerResult(Status.DENIED, "", "", -1, 0,
                false, "DENIED", message, "", "");
    }

    public static SandboxWorkerResult workerUnavailable(String message) {
        return new SandboxWorkerResult(Status.ERROR, "", "", -1, 0,
                false, "WORKER_UNAVAILABLE", message, "", "");
    }
}
