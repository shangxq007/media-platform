package com.example.platform.graph.internal;

import com.example.platform.graph.api.DirectedGraphView;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Internal in-memory implementation of {@link DirectedGraphView}.
 *
 * <p>Stores identity-preserving forward adjacency without inventing a generic
 * node order. Consumers that require canonical ordering supply it explicitly
 * to the relevant graph algorithm.
 */
public final class AdjacencyDirectedGraphView<N> implements DirectedGraphView<N> {

    private final Map<N, Set<N>> forward;
    private final Set<N> nodes;
    private final int edgeCount;

    public AdjacencyDirectedGraphView(Map<N, Set<N>> adjacencyMap) {
        this.forward = new HashMap<>();
        Set<N> allNodes = new HashSet<>(adjacencyMap.keySet());
        for (Set<N> successors : adjacencyMap.values()) {
            allNodes.addAll(successors);
        }
        int totalEdges = 0;
        for (N node : allNodes) {
            Set<N> successors = adjacencyMap.getOrDefault(node, Set.of());
            Set<N> identityPreservingSuccessors = Set.copyOf(successors);
            this.forward.put(node, identityPreservingSuccessors);
            totalEdges += identityPreservingSuccessors.size();
        }
        this.nodes = Set.copyOf(allNodes);
        this.edgeCount = totalEdges;
    }

    @Override
    public Set<N> nodes() {
        return nodes;
    }

    @Override
    public Set<N> successors(N node) {
        if (!forward.containsKey(node)) {
            throw new IllegalArgumentException("Node not in graph: " + node);
        }
        return forward.get(node);
    }

    @Override
    public Set<N> predecessors(N node) {
        if (!forward.containsKey(node)) {
            throw new IllegalArgumentException("Node not in graph: " + node);
        }
        return forward.entrySet().stream()
                .filter(e -> e.getValue().contains(node))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int nodeCount() {
        return nodes.size();
    }

    @Override
    public int edgeCount() {
        return edgeCount;
    }
}
