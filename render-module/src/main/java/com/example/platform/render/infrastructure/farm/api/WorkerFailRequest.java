package com.example.platform.render.infrastructure.farm.api;

/**
 * Request from a worker when a job fails.
 */
public record WorkerFailRequest(
        String jobId,
        String errorCode,
        String errorMessage,
        boolean retryable,
        String logsUri
) {
    public WorkerFailRequest {
        if (retryable == false && errorCode == null) errorCode = "RENDER_FAILED";
    }
}
