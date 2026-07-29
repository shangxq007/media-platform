package com.example.platform.graph.internal;

import com.example.platform.graph.api.BidirectionalGraphView;

import java.util.*;

/**
 * Internal bidirectional graph view implementation.
 *
 * <p>Precomputes reverse adjacency for O(1) predecessor lookup. Caches descendant
 * and ancestor computations lazily.
 *
 * @param <N> node type
 */
public final class BidirectionalAdjacencyGraphView<N> implements BidirectionalGraphView<N> {

    private final Map<N, Set<N>> forward;
    private final Map<N, Set<N>> reverse;
    private final Set<N> nodes;
    private final int edgeCount;
    private final Comparator<N> comparator;

    // Cached reachability (lazy)
    private volatile Map<N, Set<N>> descendantCache;
    private volatile Map<N, Set<N>> ancestorCache;
    private volatile Boolean acyclic;

    public BidirectionalAdjacencyGraphView(Map<N, Set<N>> adjacencyMap) {
        this.comparator = Comparator.comparing(Object::toString);

        Set<N> allNodes = new HashSet<>(adjacencyMap.keySet());
        for (Set<N> successors : adjacencyMap.values()) {
            allNodes.addAll(successors);
        }

        this.forward = new HashMap<>();
        this.reverse = new HashMap<>();

        for (N node : allNodes) {
            forward.put(node, new TreeSet<>(comparator));
            reverse.put(node, new TreeSet<>(comparator));
        }

        int totalEdges = 0;
        for (var entry : adjacencyMap.entrySet()) {
            N from = entry.getKey();
            for (N to : entry.getValue()) {
                forward.get(from).add(to);
                reverse.get(to).add(from);
                totalEdges++;
            }
        }

        // Freeze
        Map<N, Set<N>> frozenForward = new HashMap<>();
        Map<N, Set<N>> frozenReverse = new HashMap<>();
        for (N node : allNodes) {
            frozenForward.put(node, Set.copyOf(forward.get(node)));
            frozenReverse.put(node, Set.copyOf(reverse.get(node)));
        }
        this.forward.clear();
        this.forward.putAll(frozenForward);
        this.reverse.clear();
        this.reverse.putAll(frozenReverse);
        this.nodes = Set.copyOf(allNodes);
        this.edgeCount = totalEdges;
    }

    @Override
    public Set<N> nodes() {
        return nodes;
    }

    @Override
    public Set<N> successors(N node) {
        Objects.requireNonNull(node);
        Set<N> result = forward.get(node);
        if (result == null) {
            throw new IllegalArgumentException("Node not in graph: " + node);
        }
        return result;
    }

    @Override
    public Set<N> predecessors(N node) {
        Objects.requireNonNull(node);
        Set<N> result = reverse.get(node);
        if (result == null) {
            throw new IllegalArgumentException("Node not in graph: " + node);
        }
        return result;
    }

    @Override
    public int nodeCount() {
        return nodes.size();
    }

    @Override
    public int edgeCount() {
        return edgeCount;
    }

    @Override
    public Set<N> descendants(N node) {
        Objects.requireNonNull(node);
        ensureDescendantsCached();
        Set<N> result = descendantCache.get(node);
        return result != null ? result : Set.of();
    }

    @Override
    public Set<N> ancestors(N node) {
        Objects.requireNonNull(node);
        ensureAncestorsCached();
        Set<N> result = ancestorCache.get(node);
        return result != null ? result : Set.of();
    }

    @Override
    public boolean isAcyclic() {
        if (acyclic == null) {
            synchronized (this) {
                if (acyclic == null) {
                    acyclic = computeAcyclic();
                }
            }
        }
        return acyclic;
    }

    private void ensureDescendantsCached() {
        if (descendantCache == null) {
            synchronized (this) {
                if (descendantCache == null) {
                    descendantCache = computeDescendants();
                }
            }
        }
    }

    private void ensureAncestorsCached() {
        if (ancestorCache == null) {
            synchronized (this) {
                if (ancestorCache == null) {
                    ancestorCache = computeAncestors();
                }
            }
        }
    }

    private boolean computeAcyclic() {
        Map<N, Integer> inDegree = new HashMap<>();
        for (N node : nodes) {
            inDegree.put(node, reverse.get(node).size());
        }
        Deque<N> queue = new ArrayDeque<>();
        List<N> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(comparator);
        for (N node : sortedNodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }
        int processed = 0;
        while (!queue.isEmpty()) {
            N current = queue.poll();
            processed++;
            for (N successor : forward.get(current)) {
                int newDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(successor);
                }
            }
        }
        return processed == nodes.size();
    }

    private Map<N, Set<N>> computeDescendants() {
        List<N> topoOrder = computeTopologicalOrder();
        Map<N, Set<N>> descMap = new HashMap<>();
        for (N node : nodes) {
            descMap.put(node, new HashSet<>());
        }
        List<N> reversed = new ArrayList<>(topoOrder);
        Collections.reverse(reversed);
        for (N node : reversed) {
            Set<N> descendants = descMap.get(node);
            for (N successor : forward.get(node)) {
                descendants.add(successor);
                descendants.addAll(descMap.getOrDefault(successor, Set.of()));
            }
        }
        Map<N, Set<N>> frozen = new HashMap<>();
        for (var e : descMap.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return frozen;
    }

    private Map<N, Set<N>> computeAncestors() {
        List<N> topoOrder = computeTopologicalOrder();
        Map<N, Set<N>> ancMap = new HashMap<>();
        for (N node : nodes) {
            ancMap.put(node, new HashSet<>());
        }
        for (N node : topoOrder) {
            Set<N> ancestors = ancMap.get(node);
            for (N predecessor : reverse.get(node)) {
                ancestors.add(predecessor);
                ancestors.addAll(ancMap.getOrDefault(predecessor, Set.of()));
            }
        }
        Map<N, Set<N>> frozen = new HashMap<>();
        for (var e : ancMap.entrySet()) {
            frozen.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        return frozen;
    }

    private List<N> computeTopologicalOrder() {
        Map<N, Integer> inDegree = new HashMap<>();
        for (N node : nodes) {
            inDegree.put(node, reverse.get(node).size());
        }
        TreeSet<N> ready = new TreeSet<>(comparator);
        for (N node : nodes) {
            if (inDegree.get(node) == 0) {
                ready.add(node);
            }
        }
        List<N> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            N current = ready.pollFirst();
            order.add(current);
            for (N successor : forward.get(current)) {
                int newDegree = inDegree.merge(successor, -1, Integer::sum);
                if (newDegree == 0) {
                    ready.add(successor);
                }
            }
        }
        return List.copyOf(order);
    }
}