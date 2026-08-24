package com.example.platform.workerfabric.domain;

/**
 * Backend-local execution correlation bound to one platform attempt and ownership generation.
 *
 * <p>A handle is deliberately not an {@code ExecutableTaskId}. Canonical task identity remains on
 * the provider-bound executable task and is checked separately at fenced transitions.
 */
public sealed interface BackendExecutionHandle
        permits NativeWorkerBackendExecutionHandle,
                OpenCueBackendExecutionHandle,
                RemoteProviderExecutionHandle {

    ExecutionAttemptId executionAttemptId();

    ExecutionOwnershipGeneration ownershipGeneration();

    ExecutionBackend backend();
}
