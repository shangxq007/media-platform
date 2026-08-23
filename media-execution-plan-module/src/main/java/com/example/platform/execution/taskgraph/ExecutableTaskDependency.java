package com.example.platform.execution.taskgraph;

import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.util.Objects;

/** A source dependency preserved between two separately schedulable primary tasks. */
public record ExecutableTaskDependency(
        ExecutableTaskId producerTaskId,
        ExecutableTaskId consumerTaskId,
        LogicalDependencyEdge sourceDependency) {

    public ExecutableTaskDependency {
        Objects.requireNonNull(producerTaskId, "producerTaskId");
        Objects.requireNonNull(consumerTaskId, "consumerTaskId");
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        if (producerTaskId.equals(consumerTaskId)) {
            throw new IllegalArgumentException("task dependency must join distinct tasks");
        }
    }
}
