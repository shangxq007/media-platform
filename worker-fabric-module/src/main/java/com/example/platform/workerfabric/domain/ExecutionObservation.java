package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical normalized evidence about backend execution.
 *
 * <p>Ingestion may record this evidence, but only a separately fenced database transition may
 * change canonical attempt or executable-task state.
 */
public record ExecutionObservation(
        ObservationId observationId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        BackendExecutionHandle backendExecutionHandle,
        ProviderBindingPin providerBindingPin,
        ObservedExecutionState observedExecutionState,
        Instant observedAt,
        Optional<ProviderDiagnosticReference> diagnosticReference) {

    public ExecutionObservation {
        Objects.requireNonNull(observationId, "observationId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(backendExecutionHandle, "backendExecutionHandle");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(observedExecutionState, "observedExecutionState");
        Objects.requireNonNull(observedAt, "observedAt");
        diagnosticReference = Objects.requireNonNull(diagnosticReference, "diagnosticReference");
        if (!executionAttemptId.equals(backendExecutionHandle.executionAttemptId())) {
            throw new IllegalArgumentException("observation and handle attempt must match");
        }
        if (!ownershipGeneration.equals(backendExecutionHandle.ownershipGeneration())) {
            throw new IllegalArgumentException("observation and handle generation must match");
        }
    }
}
