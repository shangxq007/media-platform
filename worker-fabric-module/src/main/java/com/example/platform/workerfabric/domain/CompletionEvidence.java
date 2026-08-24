package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.Objects;

/** Backend result plus expected-task/output evidence presented to the completion fence. */
public record CompletionEvidence(
        CompletionEventId completionEventId,
        BackendExecutionHandle backendExecutionHandle,
        ExecutableTaskId expectedExecutableTaskId,
        ObservedExecutionState backendReportedState,
        ExpectedOutputValidation expectedOutputValidation) {

    public CompletionEvidence {
        Objects.requireNonNull(completionEventId, "completionEventId");
        Objects.requireNonNull(backendExecutionHandle, "backendExecutionHandle");
        Objects.requireNonNull(expectedExecutableTaskId, "expectedExecutableTaskId");
        Objects.requireNonNull(backendReportedState, "backendReportedState");
        Objects.requireNonNull(expectedOutputValidation, "expectedOutputValidation");
    }
}
