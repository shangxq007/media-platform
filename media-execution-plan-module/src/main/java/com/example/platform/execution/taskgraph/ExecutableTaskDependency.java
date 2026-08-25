package com.example.platform.execution.taskgraph;

import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import java.util.Objects;

/** A source dependency preserved between two separately schedulable primary tasks. */
public record ExecutableTaskDependency(
        ExecutableTaskId producerTaskId,
        ExecutableTaskId consumerTaskId,
        LogicalDependencyEdge sourceDependency,
        ExecutableInputProjection consumerInput) {

    public ExecutableTaskDependency {
        Objects.requireNonNull(producerTaskId, "producerTaskId");
        Objects.requireNonNull(consumerTaskId, "consumerTaskId");
        Objects.requireNonNull(sourceDependency, "sourceDependency");
        Objects.requireNonNull(consumerInput, "consumerInput");
        if (producerTaskId.equals(consumerTaskId)) {
            throw new IllegalArgumentException("task dependency must join distinct tasks");
        }
        if (consumerInput.producerStepId().isEmpty()
                || consumerInput.sourceArtifactPresence()
                        != ExecutableInputProjection.SourceArtifactPresence.ABSENT) {
            throw new IllegalArgumentException(
                    "task dependency must retain its exact computed consumer input semantics");
        }
    }
}
