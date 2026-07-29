package com.example.platform.graph.result;

import java.util.List;

/**
 * Result of a stable topological sort on a directed graph.
 *
 * <p>The topological order is deterministic: same graph + same natural node order
 * always produces the same topological order, regardless of insertion order or
 * internal iteration.
 *
 * @param <N> node type
 */
public sealed interface TopologicalOrderResult<N> {

    /**
     * Returns the nodes in topological order.
     *
     * <p>For every edge (u → v), u appears before v in the list.
     */
    List<N> order();

    /**
     * Result when the graph is a valid DAG.
     *
     * @param order nodes in deterministic topological order
     */
    record Ordered<N>(List<N> order) implements TopologicalOrderResult<N> {
        public Ordered {
            order = List.copyOf(order);
        }
    }

    /**
     * Result when a cycle prevents topological ordering.
     *
     * @param cycleNodes witness nodes of a cycle
     */
    record CycleDetected<N>(List<N> cycleNodes) implements TopologicalOrderResult<N> {
        public CycleDetected {
            if (cycleNodes == null || cycleNodes.isEmpty()) {
                throw new IllegalArgumentException("Cycle nodes must not be empty");
            }
            cycleNodes = List.copyOf(cycleNodes);
        }

        @Override
        public List<N> order() {
            throw new IllegalStateException("No topological order exists: cycle detected");
        }
    }

    /**
     * Factory: successful topological order.
     */
    static <N> TopologicalOrderResult<N> ordered(List<N> order) {
        return new Ordered<>(order);
    }

    /**
     * Factory: cycle detected.
     */
    static <N> TopologicalOrderResult<N> cycleDetected(List<N> cycleNodes) {
        return new CycleDetected<>(cycleNodes);
    }
}