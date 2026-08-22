package com.example.platform.execution.domain;

import java.time.Instant;
import java.util.List;

/**
 * FROZEN interface for execution providers.
 *
 * <p>Executes a compiled manifest against a specific runtime environment
 * (local, container, Kubernetes, OpenCue, Cloud Batch).
 *
 * <p>This interface is FROZEN — do not implement. Execution providers are out of scope for V1.
 */
public sealed interface ExecutionProvider permits
        ExecutionProvider.Stub {

    /**
     * Submits a manifest for execution.
     *
     * @param manifest the compiled execution manifest
     * @return the execution attempt
     * @throws UnsupportedOperationException always — not implemented
     */
    ExecutionAttempt submit(ExecutionManifest manifest);

    /**
     * Returns the capabilities of this provider.
     *
     * @return provider capabilities
     */
    ProviderCapabilities capabilities();

    /**
     * Stub implementation that always throws UnsupportedOperationException.
     */
    record Stub() implements ExecutionProvider {
        @Override
        public ExecutionAttempt submit(ExecutionManifest manifest) {
            throw new UnsupportedOperationException(
                    "ExecutionProvider is not yet implemented");
        }

        @Override
        public ProviderCapabilities capabilities() {
            return new ProviderCapabilities("stub", false);
        }
    }

    /**
     * FROZEN interface for execution attempts.
     *
     * <p>Represents a single attempt to execute a compiled manifest.
     * Tracks status, progress, and results.
     */
    sealed interface ExecutionAttempt permits
            ExecutionAttempt.Stub {

        /**
         * Returns the attempt ID.
         */
        String attemptId();

        /**
         * Returns the current status of the attempt.
         */
        AttemptStatus status();

        /**
         * Returns the time the attempt was started.
         */
        Instant startedAt();

        /**
         * Returns the time the attempt completed (if completed).
         */
        Instant completedAt();

        /**
         * Returns the outputs produced by this attempt.
         */
        List<String> outputHandles();

        record Stub(
                String attemptId,
                AttemptStatus status,
                Instant startedAt,
                Instant completedAt,
                List<String> outputHandles
        ) implements ExecutionAttempt {
        }
    }

    /**
     * Status of an execution attempt.
     */
    enum AttemptStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Capabilities of an execution provider.
     */
    record ProviderCapabilities(
            String providerType,
            boolean supportsGpu) {
    }

    /**
     * Placeholder for backend-specific execution manifest.
     * Not implemented in V1.
     */
    record ExecutionManifest(
            String backendType,
            String manifestData) {
    }
}
