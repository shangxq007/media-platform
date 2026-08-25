package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import java.util.Objects;

/**
 * Typed runtime invocation command scoped to an existing platform execution attempt.
 *
 * <p>It is runtime mechanics only, not canonical media state, operation authority, scheduler
 * identity, lease identity, or an independent execution lifecycle authority.
 */
public record ExecutionCommand(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        ExecutionAttemptId platformExecutionAttemptId,
        ExecutionOwnershipGeneration platformOwnershipGeneration,
        int sequence,
        InvocationSpec invocationSpec) {

    public ExecutionCommand {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(platformExecutionAttemptId, "platformExecutionAttemptId");
        Objects.requireNonNull(platformOwnershipGeneration, "platformOwnershipGeneration");
        Objects.requireNonNull(invocationSpec, "invocationSpec");
        if (sequence < 0) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.MALFORMED_NATIVE_PLAN,
                    "execution command sequence must be non-negative");
        }
    }
}
