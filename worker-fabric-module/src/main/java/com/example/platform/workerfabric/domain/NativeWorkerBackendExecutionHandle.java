package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Native Pull backend handle correlated by the granted platform lease. */
public record NativeWorkerBackendExecutionHandle(
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        ExecutionBackend backend,
        LeaseId leaseId)
        implements BackendExecutionHandle {

    public NativeWorkerBackendExecutionHandle {
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(leaseId, "leaseId");
        if (backend != ExecutionBackend.NATIVE_PULL_WORKER) {
            throw new IllegalArgumentException("native handle requires NATIVE_PULL_WORKER");
        }
    }

    public static NativeWorkerBackendExecutionHandle forLease(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation,
            LeaseId leaseId) {
        return new NativeWorkerBackendExecutionHandle(
                attemptId, generation, ExecutionBackend.NATIVE_PULL_WORKER, leaseId);
    }
}
