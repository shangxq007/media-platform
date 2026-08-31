package com.example.platform.workerfabric.application;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable durable execution-attempt snapshot selected only by exact ExecutionAttemptId. */
public record ExecutionReadSnapshot(
        ExecutionAttempt attempt,
        Instant createdAt,
        Instant updatedAt,
        Optional<ProviderBindingPin> providerBindingPin,
        Optional<AssignmentReadSnapshot> assignment,
        List<ExecutionObservationReadSnapshot> observations) {

    public ExecutionReadSnapshot {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        providerBindingPin = Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        assignment = Objects.requireNonNull(assignment, "assignment");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        if (updatedAt.isBefore(createdAt)
                || assignment.filter(value ->
                        !attempt.id().equals(value.assignment().executionAttemptId())
                                || !attempt.executableTaskId().equals(
                                        value.assignment().executableTaskId())
                                || !attempt.ownershipGeneration().equals(
                                        value.assignment().ownershipGeneration()))
                        .isPresent()
                || observations.stream().anyMatch(value -> value == null
                        || !attempt.id().equals(value.executionAttemptId())
                        || !attempt.ownershipGeneration().equals(value.ownershipGeneration()))) {
            throw new IllegalArgumentException("execution snapshot has mismatched owner facts");
        }
    }
}
