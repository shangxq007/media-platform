package com.example.platform.graph.internal;

import com.example.platform.graph.api.DirectedGraphView;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Internal in-memory implementation of {@link DirectedGraphView}.
 *
 * <p>Stores forward adjacency in a deterministic structure for stable iteration.
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
        List<N> sortedNodes = new ArrayList<>(allNodes);
        sortedNodes.sort(Comparator.comparing(Object::toString));

        int totalEdges = 0;
        for (N node : sortedNodes) {
            Set<N> successors = adjacencyMap.getOrDefault(node, Set.of());
            Set<N> sortedSuccessors = new TreeSet<>(Comparator.comparing(Object::toString));
            sortedSuccessors.addAll(successors);
            this.forward.put(node, Set.copyOf(sortedSuccessors));
            totalEdges += sortedSuccessors.size();
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