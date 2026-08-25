package com.example.platform.execution.taskgraph;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable result of pure dependency-preserving validated-reuse pruning. */
public record ReusePruningResult(
        Set<ExecutableTaskId> tasksToExecute,
        Set<ExecutableTaskId> reusedTasks) {

    public ReusePruningResult {
        Objects.requireNonNull(tasksToExecute, "tasksToExecute");
        Objects.requireNonNull(reusedTasks, "reusedTasks");
        tasksToExecute = Set.copyOf(new TreeSet<>(tasksToExecute));
        reusedTasks = Set.copyOf(new TreeSet<>(reusedTasks));
        if (!java.util.Collections.disjoint(tasksToExecute, reusedTasks)) {
            throw new IllegalArgumentException("executed and reused task sets must be disjoint");
        }
    }
}
