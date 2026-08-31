package com.example.platform.workerfabric.application;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.workerfabric.domain.BackendExecutionHandle;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.ObservationId;
import com.example.platform.workerfabric.domain.ObservedExecutionState;
import com.example.platform.workerfabric.domain.ProviderDiagnosticReference;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact subset retained by {@code wf_execution_observation}; unavailable ingestion fields remain
 * explicit unknowns rather than being reconstructed from the attempt or callback text.
 */
public record ExecutionObservationReadSnapshot(
        ObservationId observationId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        ObservedExecutionState observedExecutionState,
        boolean currentEvidence,
        Instant observedAt,
        Optional<BackendExecutionHandle> backendExecutionHandle,
        Optional<ProviderBindingPin> providerBindingPin,
        Optional<ProviderDiagnosticReference> diagnosticReference) {

    public ExecutionObservationReadSnapshot {
        Objects.requireNonNull(observationId, "observationId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(observedExecutionState, "observedExecutionState");
        Objects.requireNonNull(observedAt, "observedAt");
        backendExecutionHandle = Objects.requireNonNull(
                backendExecutionHandle, "backendExecutionHandle");
        providerBindingPin = Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        diagnosticReference = Objects.requireNonNull(
                diagnosticReference, "diagnosticReference");
        backendExecutionHandle.ifPresent(handle -> {
            if (!executionAttemptId.equals(handle.executionAttemptId())
                    || !ownershipGeneration.equals(handle.ownershipGeneration())) {
                throw new IllegalArgumentException(
                        "observation handle must bind the exact attempt and generation");
            }
        });
    }
}
