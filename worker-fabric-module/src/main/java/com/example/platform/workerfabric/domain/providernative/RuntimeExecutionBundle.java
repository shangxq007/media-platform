package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Ordered typed runtime commands sharing one platform attempt/generation. */
public record RuntimeExecutionBundle(
        ExecutableTaskId executableTaskId,
        ProviderBindingPin providerBindingPin,
        ExecutionAttemptId platformExecutionAttemptId,
        ExecutionOwnershipGeneration platformOwnershipGeneration,
        List<ExecutionCommand> commands) {

    public RuntimeExecutionBundle {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(platformExecutionAttemptId, "platformExecutionAttemptId");
        Objects.requireNonNull(platformOwnershipGeneration, "platformOwnershipGeneration");
        Objects.requireNonNull(commands, "commands");
        commands = commands.stream()
                .map(command -> requireScoped(
                        command,
                        executableTaskId,
                        providerBindingPin,
                        platformExecutionAttemptId,
                        platformOwnershipGeneration))
                .sorted(Comparator.comparingInt(ExecutionCommand::sequence))
                .toList();
    }

    private static ExecutionCommand requireScoped(
            ExecutionCommand command,
            ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation) {
        Objects.requireNonNull(command, "commands element");
        if (!command.executableTaskId().equals(executableTaskId)
                || !command.providerBindingPin().equals(providerBindingPin)
                || !command.platformExecutionAttemptId().equals(attemptId)
                || !command.platformOwnershipGeneration().equals(generation)) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "runtime command must share the bundle task, binding, attempt and generation");
        }
        return command;
    }
}
