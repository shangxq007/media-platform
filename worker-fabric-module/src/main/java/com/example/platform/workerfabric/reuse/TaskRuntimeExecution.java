package com.example.platform.workerfabric.reuse;

import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.util.Objects;

/** Existing attempt/generation context and output publication metadata for one graph task. */
public record TaskRuntimeExecution(
        RuntimeExecutionContext runtimeContext,
        CompletionEvidence completionEvidence,
        DurableOutputTarget durableOutputTarget,
        ArtifactCommitMetadata artifactCommitMetadata) {

    public TaskRuntimeExecution {
        Objects.requireNonNull(runtimeContext, "runtimeContext");
        Objects.requireNonNull(completionEvidence, "completionEvidence");
        Objects.requireNonNull(durableOutputTarget, "durableOutputTarget");
        Objects.requireNonNull(artifactCommitMetadata, "artifactCommitMetadata");
        if (!completionEvidence.expectedExecutableTaskId().equals(
                        runtimeContext.executableTaskId())
                || !completionEvidence.backendExecutionHandle().executionAttemptId().equals(
                        runtimeContext.platformExecutionAttemptId())
                || !completionEvidence.backendExecutionHandle().ownershipGeneration().equals(
                        runtimeContext.platformOwnershipGeneration())) {
            throw new IllegalArgumentException(
                    "completion evidence must match runtime task, attempt, and generation");
        }
    }
}
