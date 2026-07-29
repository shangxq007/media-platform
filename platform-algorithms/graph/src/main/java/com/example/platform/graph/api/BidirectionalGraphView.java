package com.example.platform.graph.api;

import java.util.Set;

/**
 * Extension of {@link DirectedGraphView} that supports bidirectional
 * traversal efficiently. Backed by a kernel that stores both forward
 * and reverse adjacency.
 *
 * @param <N> node type
 */
public interface BidirectionalGraphView<N> extends DirectedGraphView<N> {

    /**
     * Returns all nodes reachable from {@code node} via outgoing edges
     * (transitive closure of successors).
     */
    Set<N> descendants(N node);

    /**
     * Returns all nodes that can reach {@code node} via outgoing edges
     * (transitive closure of predecessors).
     */
    Set<N> ancestors(N node);

    /**
     * Returns true if there exists a path from {@code from} to {@code to}.
     * A node is always reachable from itself (reflexive).
     */
    default boolean isReachable(N from, N to) {
        return from.equals(to) || descendants(from).contains(to);
    }

    /**
     * Returns true if the graph is a DAG (no directed cycles).
     */
    boolean isAcyclic();
}