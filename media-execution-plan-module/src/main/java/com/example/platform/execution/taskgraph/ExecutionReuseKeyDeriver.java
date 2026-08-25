package com.example.platform.execution.taskgraph;

import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.result.TopologicalOrderResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure deterministic Merkle derivation for {@link ExecutionReuseKey} V1.
 *
 * <p>Task ordering reuses the platform graph-algorithms topological ordering authority
 * ({@link GraphAlgorithms#topologicalOrder}); this derivation defines no competing
 * scheduling or ordering algorithm.
 */
public final class ExecutionReuseKeyDeriver {

    private static final String CANONICAL_TAG = "roadmap22.execution-reuse-key.v1";

    private ExecutionReuseKeyDeriver() {
    }

    public static Map<ExecutableTaskId, ExecutionReuseKey> derive(
            ProviderBoundExecutableTaskGraph graph) {
        Objects.requireNonNull(graph, "graph");
        Map<ExecutableTaskId, ExecutableTask> tasks = new HashMap<>();
        graph.tasks().forEach(task -> tasks.put(task.id(), task));

        DirectedGraphView<ExecutableTaskId> topology =
                new ProviderBoundExecutableTaskGraph.TaskGraphView(
                        graph.tasks(), graph.taskDependencies());
        TopologicalOrderResult<ExecutableTaskId> orderResult =
                GraphAlgorithms.topologicalOrder(topology);
        if (orderResult instanceof TopologicalOrderResult.CycleDetected<ExecutableTaskId> cycle) {
            throw new IllegalArgumentException(
                    "ExecutionReuseKey derivation requires an acyclic graph: " + cycle.cycleNodes());
        }
        List<ExecutableTaskId> order =
                ((TopologicalOrderResult.Ordered<ExecutableTaskId>) orderResult).order();

        Map<ExecutableTaskId, List<ExecutableTaskDependency>> incoming = new HashMap<>();
        for (ExecutableTaskDependency dependency : graph.taskDependencies()) {
            incoming.computeIfAbsent(dependency.consumerTaskId(), ignored -> new ArrayList<>())
                    .add(dependency);
        }

        Map<ExecutableTaskId, ExecutionReuseKey> derived = new LinkedHashMap<>();
        for (ExecutableTaskId taskId : order) {
            ExecutableTask task = tasks.get(taskId);
            List<String> predecessorInputs = incoming.getOrDefault(taskId, List.of()).stream()
                    .map(dependency -> predecessorContribution(graph, tasks, derived, dependency))
                    .sorted()
                    .toList();
            String payload = new CanonicalWriter()
                    .tag(CANONICAL_TAG)
                    .field("schemaVersion", ExecutionReuseKey.VERSION)
                    .field("executableTaskId", task.id().sha256Hex())
                    .field("localTaskSemantics", ExecutableTaskCanonicalCodec.taskSemantics(
                            task.compositionDecision(),
                            task.boundaryActions(),
                            task.requiredInputArtifactPins()))
                    .field("computedPredecessorInputs", canonicalList(predecessorInputs))
                    .build();
            derived.put(taskId, ExecutionReuseKey.fromCanonical(CANONICAL_TAG + payload));
        }
        return Map.copyOf(derived);
    }

    private static String predecessorContribution(
            ProviderBoundExecutableTaskGraph graph,
            Map<ExecutableTaskId, ExecutableTask> tasks,
            Map<ExecutableTaskId, ExecutionReuseKey> derived,
            ExecutableTaskDependency dependency) {
        ExecutionReuseKey predecessor = derived.get(dependency.producerTaskId());
        if (predecessor == null) {
            throw new IllegalArgumentException("predecessor ExecutionReuseKey is unavailable");
        }
        OutputDeclaration output = exactProducerOutput(
                tasks.get(dependency.producerTaskId()), dependency);
        List<ExecutionArtifactBoundary> boundaries = graph.executionArtifactBoundaries().stream()
                .filter(boundary -> boundary.sourceDependency().equals(dependency.sourceDependency()))
                .toList();
        if (boundaries.size() > 1) {
            throw new IllegalArgumentException("ambiguous execution Artifact boundary");
        }
        return new CanonicalWriter()
                .tag("roadmap22.execution-reuse-predecessor-input.v1")
                .field("predecessorKeyVersion", predecessor.version())
                .field("predecessorKeyDigest", predecessor.stableDigest())
                .field("producerOutput", ExecutableTaskCanonicalCodec.output(output))
                .field("dependencySemantics",
                        ExecutableTaskCanonicalCodec.dependency(dependency.sourceDependency()))
                .field("boundarySemantics", boundaries.isEmpty()
                        ? "direct-compatible"
                        : ExecutableTaskCanonicalCodec.executionArtifactBoundary(boundaries.getFirst()))
                .build();
    }

    private static OutputDeclaration exactProducerOutput(
            ExecutableTask producer,
            ExecutableTaskDependency dependency) {
        List<OutputDeclaration> matches = producer.memberships().stream()
                .flatMap(membership -> membership.outputMapping().stream())
                .filter(output -> output.logicalNodeId().equals(
                        dependency.sourceDependency().producerLogicalNodeId()))
                .filter(output -> output.sourceRenderNodeId().equals(
                        dependency.sourceDependency().producerRenderNodeId()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("dependency must resolve one exact producer output");
        }
        return matches.getFirst();
    }

    private static String canonicalList(Collection<String> values) {
        List<String> canonical = new ArrayList<>(values);
        canonical.sort(Comparator.naturalOrder());
        return new CanonicalWriter().list(canonical).build();
    }
}
