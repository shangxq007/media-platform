package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable downstream selection view enforcing one active placement authority per task.
 *
 * <p>It references but never rewrites the provider-bound ETG.
 */
public final class ExecutionBackendSelectionSet {

    private final ProviderBoundExecutableTaskGraph providerBoundGraph;
    private final List<ExecutionBackendSelection> selections;

    private ExecutionBackendSelectionSet(
            ProviderBoundExecutableTaskGraph providerBoundGraph,
            List<ExecutionBackendSelection> selections) {
        this.providerBoundGraph = providerBoundGraph;
        this.selections = selections;
    }

    public static ExecutionBackendSelectionSet forGraph(
            ProviderBoundExecutableTaskGraph graph,
            Collection<ExecutionBackendSelection> selections) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(selections, "selections");
        Map<ExecutableTaskId, ExecutableTask> graphTasks = new HashMap<>();
        graph.tasks().forEach(task -> graphTasks.put(task.id(), task));

        Map<ExecutableTaskId, ExecutionBackendSelection> unique = new HashMap<>();
        for (ExecutionBackendSelection selection : selections) {
            Objects.requireNonNull(selection, "selections element");
            ExecutableTask graphTask = graphTasks.get(selection.executableTaskId());
            if (graphTask == null || !selection.selectsExactTask(graphTask)) {
                throw new IllegalArgumentException(
                        "backend selection must reference an exact task in the provider-bound ETG");
            }
            if (unique.putIfAbsent(selection.executableTaskId(), selection) != null) {
                throw new IllegalArgumentException(
                        "ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1");
            }
        }
        ArrayList<ExecutionBackendSelection> canonical = new ArrayList<>(unique.values());
        canonical.sort(Comparator.comparing(ExecutionBackendSelection::executableTaskId));
        return new ExecutionBackendSelectionSet(graph, List.copyOf(canonical));
    }

    public ProviderBoundExecutableTaskGraph providerBoundGraph() {
        return providerBoundGraph;
    }

    public List<ExecutionBackendSelection> selections() {
        return selections;
    }
}
