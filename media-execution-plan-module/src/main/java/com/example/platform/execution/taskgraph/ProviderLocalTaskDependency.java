package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.util.Objects;

/** A source dependency preserved as provider-local ordering inside one task. */
public record ProviderLocalTaskDependency(
        ExecutableTaskId taskId,
        ExecutionStepId producerUnitId,
        ExecutionStepId consumerUnitId,
        LogicalDependencyEdge sourceDependency) {

    public ProviderLocalTaskDependency {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(producerUnitId, "producerUnitId");
        Objects.requireNonNull(consumerUnitId, "consumerUnitId");
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        if (producerUnitId.equals(consumerUnitId)) {
            throw new IllegalArgumentException("provider-local dependency must join distinct units");
        }
    }
}
