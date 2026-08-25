package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import java.util.Objects;

/** Minimal runtime mechanics context after upstream execution ownership has been established. */
public record RuntimeExecutionContext(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        ExecutionAttemptId platformExecutionAttemptId,
        ExecutionOwnershipGeneration platformOwnershipGeneration) {

    public RuntimeExecutionContext {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(platformExecutionAttemptId, "platformExecutionAttemptId");
        Objects.requireNonNull(platformOwnershipGeneration, "platformOwnershipGeneration");
    }
}
