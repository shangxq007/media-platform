package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** One remote-provider execution correlated to one platform attempt and generation. */
public record RemoteProviderExecutionHandle(
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        ExecutionBackend backend,
        RemoteExecutionId remoteExecutionId)
        implements BackendExecutionHandle {

    public RemoteProviderExecutionHandle {
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(remoteExecutionId, "remoteExecutionId");
        if (backend != ExecutionBackend.REMOTE_PROVIDER) {
            throw new IllegalArgumentException("remote handle requires REMOTE_PROVIDER");
        }
    }

    public static RemoteProviderExecutionHandle forRemoteExecution(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation,
            RemoteExecutionId remoteExecutionId) {
        return new RemoteProviderExecutionHandle(
                attemptId, generation, ExecutionBackend.REMOTE_PROVIDER, remoteExecutionId);
    }
}
