package com.example.platform.bmf;

import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.ExecutionResult;
import com.example.platform.sandbox.execution.TaskCapability;
import java.util.Objects;

/** Task-facing binding for the existing unsupported BMF seam. Never synthesizes execution success. */
public final class BmfExecutionBackend implements ExecutionBackend {
    @Override public String backendId() { return "bmf"; }
    @Override public boolean supports(TaskCapability capability) {
        return capability == TaskCapability.MEDIA_PIPELINE || capability == TaskCapability.TRANSCODE
                || capability == TaskCapability.FRAME_EXTRACTION || capability == TaskCapability.FILTER
                || capability == TaskCapability.THUMBNAIL;
    }
    @Override public ExecutionResult execute(ExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        if (Thread.currentThread().isInterrupted()) {
            return ExecutionResult.failure(-1, "", 0, "CANCELLED", "BMF request cancelled before execution");
        }
        if (request.jobId() == null || request.jobId().isBlank() || request.taskId() == null || request.taskId().isBlank()
                || !supports(request.taskCapability())) {
            return ExecutionResult.failure(-1, "", 0, "INVALID_EXECUTION_REQUEST", "A supported capability and platform job/task identity are required");
        }
        return ExecutionResult.failure(-1, "", 0, "RUNTIME_ADAPTER_UNSUPPORTED_PLAN",
                "BMF CPU runtime adaptation remains unsupported; no graph was executed");
    }
}
