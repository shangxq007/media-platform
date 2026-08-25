package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.execution.taskgraph.ReusePruningResult;
import java.util.Map;
import java.util.Objects;

/** Explainable graph result retaining derived keys, typed lookup decisions, pruning, and outputs. */
public record RuntimeClosedLoopResult(
        Map<ExecutableTaskId, ExecutionReuseKey> executionReuseKeys,
        Map<ExecutableTaskId, ValidatedReuseDecision> reuseDecisions,
        ReusePruningResult pruningResult,
        Map<ExecutableTaskId, ArtifactPin> outputArtifactPins,
        Map<ExecutableTaskId, RuntimeClosedLoopTaskResult> executedTaskResults) {

    public RuntimeClosedLoopResult {
        Objects.requireNonNull(executionReuseKeys, "executionReuseKeys");
        Objects.requireNonNull(reuseDecisions, "reuseDecisions");
        Objects.requireNonNull(pruningResult, "pruningResult");
        Objects.requireNonNull(outputArtifactPins, "outputArtifactPins");
        Objects.requireNonNull(executedTaskResults, "executedTaskResults");
        executionReuseKeys = Map.copyOf(executionReuseKeys);
        reuseDecisions = Map.copyOf(reuseDecisions);
        outputArtifactPins = Map.copyOf(outputArtifactPins);
        executedTaskResults = Map.copyOf(executedTaskResults);
    }
}
