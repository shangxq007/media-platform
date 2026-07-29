package com.example.platform.graph.api;

import java.util.List;
import java.util.Set;

/**
 * Read-only view of a directed graph with node type {@code N}.
 *
 * <p>This is the minimal contract for graph algorithms. It intentionally hides
 * mutation, construction details, and storage. Implementations may be backed by
 * adjacency lists, database projections, or in-memory kernels.
 *
 * <p>All operations are deterministic: same graph + same node ordering produces
 * identical results.
 *
 * @param <N> node type (must have stable {@link Object#equals}/{@link Object#hashCode})
 */
public interface DirectedGraphView<N> {

    /**
     * Returns all nodes in the graph. Order is implementation-defined but stable.
     */
    Set<N> nodes();

    /**
     * Returns the set of direct successor nodes for {@code node}.
     * Outgoing edges: {@code node → successor}.
     *
     * @return unmodifiable set of successors; empty if node has no outgoing edges
     * @throws IllegalArgumentException if node not in graph
     */
    Set<N> successors(N node);

    /**
     * Returns the set of direct predecessor nodes for {@code node}.
     * Incoming edges: {@code predecessor → node}.
     *
     * @return unmodifiable set of predecessors; empty if node has no incoming edges
     * @throws IllegalArgumentException if node not in graph
     */
    Set<N> predecessors(N node);

    /**
     * Returns the number of nodes.
     */
    int nodeCount();

    /**
     * Returns the number of directed edges.
     */
    int edgeCount();

    /**
     * Returns true if the graph contains no nodes.
     */
    default boolean isEmpty() {
        return nodeCount() == 0;
    }

    /**
     * Returns true if the graph contains the given node.
     */
    default boolean containsNode(N node) {
        return nodes().contains(node);
    }

    /**
     * Returns nodes with no incoming edges (roots / sources).
     */
    default Set<N> roots() {
        return nodes().stream()
                .filter(n -> predecessors(n).isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns nodes with no outgoing edges (sinks / leaves).
     */
    default Set<N> sinks() {
        return nodes().stream()
                .filter(n -> successors(n).isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}