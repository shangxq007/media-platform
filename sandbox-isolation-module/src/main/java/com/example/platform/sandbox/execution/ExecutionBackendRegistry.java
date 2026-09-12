package com.example.platform.sandbox.execution;

import java.util.Optional;

/** Existing task-backend lookup boundary; implementations are runtime composition, not Outbox. */
public interface ExecutionBackendRegistry {
    Optional<ExecutionBackend> resolve(TaskCapability capability);
    int size();
}
