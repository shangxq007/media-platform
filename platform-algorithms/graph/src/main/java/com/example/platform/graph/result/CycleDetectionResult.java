package com.example.platform.graph.result;

import java.util.List;
import java.util.Set;

/**
 * Result of cycle detection on a directed graph.
 *
 * <p>Either the graph is acyclic, or a cycle is present with witness nodes.
 *
 * @param <N> node type
 */
public sealed interface CycleDetectionResult<N> {

    /**
     * Returns true if the graph contains no directed cycles.
     */
    boolean isAcyclic();

    /**
     * Returns true if the graph contains at least one directed cycle.
     */
    default boolean hasCycle() {
        return !isAcyclic();
    }

    /**
     * Result when the graph is acyclic.
     */
    record Acyclic<N>() implements CycleDetectionResult<N> {
        @Override
        public boolean isAcyclic() {
            return true;
        }
    }

    /**
     * Result when a cycle is detected.
     *
     * @param cycleNodes nodes involved in the cycle (non-empty, in cycle order)
     */
    record Cyclic<N>(List<N> cycleNodes) implements CycleDetectionResult<N> {
        public Cyclic {
            if (cycleNodes == null || cycleNodes.isEmpty()) {
                throw new IllegalArgumentException("Cycle nodes must not be empty");
            }
            cycleNodes = List.copyOf(cycleNodes);
        }

        @Override
        public boolean isAcyclic() {
            return false;
        }
    }

    /**
     * Factory: acyclic result.
     */
    @SuppressWarnings("unchecked")
    static <N> CycleDetectionResult<N> acyclic() {
        return (CycleDetectionResult<N>) new Acyclic<N>();
    }

    /**
     * Factory: cyclic result with witness nodes.
     */
    static <N> CycleDetectionResult<N> cyclic(List<N> cycleNodes) {
        return new Cyclic<>(cycleNodes);
    }
}