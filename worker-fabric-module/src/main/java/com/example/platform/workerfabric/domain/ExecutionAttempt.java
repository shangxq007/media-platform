package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.Objects;
import java.util.Optional;

/** One backend-neutral A3 lifecycle instance bound to an exact ownership generation. */
public record ExecutionAttempt(
        ExecutionAttemptId id,
        ExecutableTaskId executableTaskId,
        ExecutionOwnershipGeneration ownershipGeneration,
        ExecutionBackend backend,
        ExecutionAttemptState state,
        Optional<BackendExecutionHandle> backendExecutionHandle) {

    public ExecutionAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(state, "state");
        backendExecutionHandle = Objects.requireNonNull(
                backendExecutionHandle, "backendExecutionHandle");
        backendExecutionHandle.ifPresent(handle -> {
            if (!handle.executionAttemptId().equals(id)
                    || !handle.ownershipGeneration().equals(ownershipGeneration)
                    || handle.backend() != backend) {
                throw new IllegalArgumentException(
                        "backend handle must bind the exact attempt, generation, and backend");
            }
        });
    }
}
