package com.example.platform.graph.api;

import com.example.platform.graph.internal.AdjacencyDirectedGraphView;
import com.example.platform.graph.internal.BidirectionalAdjacencyGraphView;

import java.util.Set;

/**
 * Factory for constructing directed graph views.
 *
 * <p>Use this to build graph projections from domain models without exposing
 * internal storage.
 */
public final class GraphViews {

    private GraphViews() {
    }

    /**
     * Creates a directed graph view from explicit adjacency lists.
     *
     * @param adjacencyMap node -> successors (outgoing edges)
     * @param <N>          node type
     * @return immutable directed graph view
     */
    public static <N> DirectedGraphView<N> directedFromAdjacency(
            java.util.Map<N, Set<N>> adjacencyMap) {
        return new AdjacencyDirectedGraphView<>(java.util.Map.copyOf(adjacencyMap));
    }

    /**
     * Creates a bidirectional graph view from explicit adjacency lists.
     * Precomputes reverse adjacency for efficient ancestor queries.
     *
     * @param adjacencyMap node -> successors (outgoing edges)
     * @param <N>          node type
     * @return bidirectional graph view
     */
    public static <N> BidirectionalGraphView<N> bidirectionalFromAdjacency(
            java.util.Map<N, Set<N>> adjacencyMap) {
        return new BidirectionalAdjacencyGraphView<>(java.util.Map.copyOf(adjacencyMap));
    }

    /**
     * Creates a directed graph view from a list of edges.
     *
     * @param nodes node set
     * @param edges list of [from, to] pairs
     * @param <N>   node type
     * @return immutable directed graph view
     */
    public static <N> DirectedGraphView<N> directedFromEdges(Set<N> nodes, java.util.List<java.util.Map.Entry<N, N>> edges) {
        java.util.Map<N, java.util.Set<N>> adjacency = new java.util.HashMap<>();
        for (N node : nodes) {
            adjacency.put(node, new java.util.HashSet<>());
        }
        for (java.util.Map.Entry<N, N> edge : edges) {
            adjacency.computeIfAbsent(edge.getKey(), k -> new java.util.HashSet<>()).add(edge.getValue());
        }
        java.util.Map<N, Set<N>> frozen = new java.util.HashMap<>();
        for (var e : adjacency.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return directedFromAdjacency(frozen);
    }
}