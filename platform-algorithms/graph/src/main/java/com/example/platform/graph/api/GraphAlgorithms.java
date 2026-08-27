package com.example.platform.graph.api;

import com.example.platform.graph.result.CycleDetectionResult;
import com.example.platform.graph.result.ReachabilityResult;
import com.example.platform.graph.result.TopologicalOrderResult;

import java.util.*;

/**
 * Pure functional graph algorithms operating on {@link DirectedGraphView}.
 *
 * <p>All methods are side-effect free and hold no mutable state. Operations
 * whose result requires canonical tie-breaking accept the platform-owned node
 * order explicitly; generic graph nodes have no implicit semantic order.
 */
public final class GraphAlgorithms {

    private GraphAlgorithms() {
    }

    // ── Cycle Detection ─────────────────────────────────────────────────────

    /**
     * Detects whether the graph contains a directed cycle.
     *
     * @param graph directed graph view
     * @param <N>   node type
     * @return cycle detection result
     */
    public static <N> CycleDetectionResult<N> detectCycles(DirectedGraphView<N> graph) {
        Objects.requireNonNull(graph);

        if (graph.isEmpty()) {
            return CycleDetectionResult.acyclic();
        }

        Map<N, Integer> inDegree = new HashMap<>();
        for (N node : graph.nodes()) {
            inDegree.put(node, graph.predecessors(node).size());
        }

        Deque<N> queue = new ArrayDeque<>();
        for (N node : graph.nodes()) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            N current = queue.poll();
            processed++;
            for (N successor : graph.successors(current)) {
                int newDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(successor);
                }
            }
        }

        if (processed == graph.nodeCount()) {
            return CycleDetectionResult.acyclic();
        }

        List<N> cycleNodes = new ArrayList<>();
        for (N node : graph.nodes()) {
            if (inDegree.getOrDefault(node, 0) > 0) {
                cycleNodes.add(node);
            }
        }
        return CycleDetectionResult.cyclic(cycleNodes);
    }

    // ── Topological Order ───────────────────────────────────────────────────

    /**
     * Computes a canonical topological ordering using an explicit platform
     * order for dependency-independent tie-breaking.
     *
     * <p>The comparator must be a strict total order over distinct semantic
     * nodes. In particular, it must not compare unequal nodes as equal.
     *
     * @throws IllegalArgumentException if the supplied comparator collapses
     *         distinct graph nodes or is not a strict total order
     */
    public static <N> TopologicalOrderResult<N> topologicalOrder(
            DirectedGraphView<N> graph,
            Comparator<? super N> nodeOrder) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(nodeOrder);

        validateStrictTotalOrder(graph.nodes(), nodeOrder);
        Map<N, Integer> inDegree = new HashMap<>();
        for (N node : graph.nodes()) {
            inDegree.put(node, graph.predecessors(node).size());
        }

        PriorityQueue<N> ready = new PriorityQueue<>(nodeOrder);
        for (N node : graph.nodes()) {
            if (inDegree.get(node) == 0) {
                ready.add(node);
            }
        }

        List<N> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            N current = ready.poll();
            order.add(current);
            for (N successor : graph.successors(current)) {
                int newDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newDegree == 0) {
                    ready.add(successor);
                }
            }
        }

        if (order.size() < graph.nodeCount()) {
            List<N> cycleNodes = graph.nodes().stream()
                    .filter(n -> !order.contains(n))
                    .sorted(nodeOrder)
                    .toList();
            return TopologicalOrderResult.cycleDetected(cycleNodes);
        }

        return TopologicalOrderResult.ordered(List.copyOf(order));
    }

    private static <N> void validateStrictTotalOrder(
            Set<N> nodes,
            Comparator<? super N> nodeOrder) {
        List<N> nodeList = List.copyOf(nodes);
        for (N node : nodeList) {
            if (nodeOrder.compare(node, node) != 0) {
                throw new IllegalArgumentException("nodeOrder must compare each node equal to itself");
            }
        }
        for (int leftIndex = 0; leftIndex < nodeList.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < nodeList.size(); rightIndex++) {
                N left = nodeList.get(leftIndex);
                N right = nodeList.get(rightIndex);
                int forward = Integer.signum(nodeOrder.compare(left, right));
                int reverse = Integer.signum(nodeOrder.compare(right, left));
                if (!left.equals(right) && forward == 0) {
                    throw new IllegalArgumentException(
                            "nodeOrder must distinguish all distinct semantic graph nodes");
                }
                if (forward != -reverse) {
                    throw new IllegalArgumentException("nodeOrder must be asymmetric");
                }
            }
        }
        List<N> sorted = new ArrayList<>(nodeList);
        try {
            sorted.sort(nodeOrder);
        } catch (IllegalArgumentException invalidComparator) {
            throw new IllegalArgumentException("nodeOrder must be transitive", invalidComparator);
        }
        for (int leftIndex = 0; leftIndex < sorted.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < sorted.size(); rightIndex++) {
                if (nodeOrder.compare(sorted.get(leftIndex), sorted.get(rightIndex)) >= 0) {
                    throw new IllegalArgumentException("nodeOrder must be transitive");
                }
            }
        }
    }

    // ── Reachability ────────────────────────────────────────────────────────

    /**
     * Computes all nodes reachable from the given source nodes (BFS).
     */
    public static <N> Set<N> reachableFrom(DirectedGraphView<N> graph, Set<N> sources) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(sources);

        Set<N> visited = new HashSet<>();
        Deque<N> queue = new ArrayDeque<>(sources);
        visited.addAll(sources);

        while (!queue.isEmpty()) {
            N current = queue.poll();
            for (N successor : graph.successors(current)) {
                if (visited.add(successor)) {
                    queue.add(successor);
                }
            }
        }
        return Set.copyOf(visited);
    }

    /**
     * Computes reachability with result wrapper.
     */
    public static <N> ReachabilityResult<N> reachability(DirectedGraphView<N> graph, Set<N> sources) {
        return ReachabilityResult.full(Set.copyOf(sources), reachableFrom(graph, sources));
    }

    // ── Bounded Ancestors / Descendants ─────────────────────────────────────

    /**
     * Computes descendants up to a maximum depth (BFS with depth limit).
     */
    public static <N> Set<N> descendantsBounded(DirectedGraphView<N> graph, N node, int maxDepth) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(node);
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be non-negative");

        Set<N> visited = new HashSet<>();
        Deque<N> queue = new ArrayDeque<>();
        queue.add(node);
        visited.add(node);

        int depth = 0;
        int nodesAtCurrentLevel = 1;
        int nodesAtNextLevel = 0;

        while (!queue.isEmpty() && depth < maxDepth) {
            N current = queue.poll();
            nodesAtCurrentLevel--;

            for (N successor : graph.successors(current)) {
                if (visited.add(successor)) {
                    queue.add(successor);
                    nodesAtNextLevel++;
                }
            }

            if (nodesAtCurrentLevel == 0) {
                depth++;
                nodesAtCurrentLevel = nodesAtNextLevel;
                nodesAtNextLevel = 0;
            }
        }
        return Set.copyOf(visited);
    }

    /**
     * Computes ancestors up to a maximum depth (reverse BFS with depth limit).
     */
    public static <N> Set<N> ancestorsBounded(DirectedGraphView<N> graph, N node, int maxDepth) {
        Objects.requireNonNull(graph);
        Objects.requireNonNull(node);
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth must be non-negative");

        Set<N> visited = new HashSet<>();
        Deque<N> queue = new ArrayDeque<>();
        queue.add(node);
        visited.add(node);

        int depth = 0;
        int nodesAtCurrentLevel = 1;
        int nodesAtNextLevel = 0;

        while (!queue.isEmpty() && depth < maxDepth) {
            N current = queue.poll();
            nodesAtCurrentLevel--;

            for (N predecessor : graph.predecessors(current)) {
                if (visited.add(predecessor)) {
                    queue.add(predecessor);
                    nodesAtNextLevel++;
                }
            }

            if (nodesAtCurrentLevel == 0) {
                depth++;
                nodesAtCurrentLevel = nodesAtNextLevel;
                nodesAtNextLevel = 0;
            }
        }
        return Set.copyOf(visited);
    }
}
