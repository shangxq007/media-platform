package com.example.platform.execution.taskgraph;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure backward-closure pruning. A validated reused task satisfies its output boundary and stops
 * traversal; every other requested task retains all transitive producer dependencies.
 */
public final class DependencyPreservingReusePruner {

    private DependencyPreservingReusePruner() {
    }

    public static ReusePruningResult prune(
            ProviderBoundExecutableTaskGraph graph,
            Set<ExecutableTaskId> requestedTasks,
            Set<ExecutableTaskId> validatedReusedTasks) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedTasks, "requestedTasks");
        Objects.requireNonNull(validatedReusedTasks, "validatedReusedTasks");
        Set<ExecutableTaskId> graphTasks = graph.tasks().stream()
                .map(ExecutableTask::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!graphTasks.containsAll(requestedTasks)) {
            throw new IllegalArgumentException("requested task is absent from graph");
        }
        if (!graphTasks.containsAll(validatedReusedTasks)) {
            throw new IllegalArgumentException("validated reused task is absent from graph");
        }

        Map<ExecutableTaskId, List<ExecutableTaskId>> predecessors = new HashMap<>();
        for (ExecutableTaskDependency dependency : graph.taskDependencies()) {
            predecessors.computeIfAbsent(dependency.consumerTaskId(), ignored ->
                    new java.util.ArrayList<>()).add(dependency.producerTaskId());
        }
        Set<ExecutableTaskId> execute = new HashSet<>();
        Set<ExecutableTaskId> reused = new HashSet<>();
        ArrayDeque<ExecutableTaskId> pending = new ArrayDeque<>(requestedTasks);
        Set<ExecutableTaskId> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            ExecutableTaskId current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (validatedReusedTasks.contains(current)) {
                reused.add(current);
                continue;
            }
            execute.add(current);
            pending.addAll(predecessors.getOrDefault(current, List.of()));
        }
        return new ReusePruningResult(execute, reused);
    }
}
