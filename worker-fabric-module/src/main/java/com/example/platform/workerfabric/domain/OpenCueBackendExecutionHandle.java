package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** One OpenCue submission correlated to one platform attempt and generation. */
public record OpenCueBackendExecutionHandle(
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        ExecutionBackend backend,
        CueJobId cueJobId)
        implements BackendExecutionHandle {

    public OpenCueBackendExecutionHandle {
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(cueJobId, "cueJobId");
        if (backend != ExecutionBackend.OPEN_CUE_FARM) {
            throw new IllegalArgumentException("OpenCue handle requires OPEN_CUE_FARM");
        }
    }

    public static OpenCueBackendExecutionHandle forSubmission(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation,
            CueJobId cueJobId) {
        return new OpenCueBackendExecutionHandle(
                attemptId, generation, ExecutionBackend.OPEN_CUE_FARM, cueJobId);
    }
}
