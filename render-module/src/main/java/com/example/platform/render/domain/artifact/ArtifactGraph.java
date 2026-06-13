package com.example.platform.render.domain.artifact;

import java.time.Instant;
import java.util.*;

/**
 * Immutable directed acyclic graph (DAG) of artifact nodes.
 *
 * <p>An ArtifactGraph represents all outputs from a render job execution.
 * It tracks lineage, enables caching, and supports incremental rendering.
 *
 * <p>Key properties:
 * <ul>
 *   <li>Immutable once created</li>
 *   <li>Root artifact is the primary output</li>
 *   <li>Nodes form a DAG (no cycles)</li>
 *   <li>Hash-based deduplication</li>
 * </ul>
 */
public record ArtifactGraph(
        String graphId,
        String jobId,
        String rootArtifactId,
        Map<String, ArtifactNode> nodes,
        Instant createdAt,
        int version
) {
    /**
     * Create a new artifact graph with a single root node.
     */
    public static ArtifactGraph create(String jobId, ArtifactNode rootNode) {
        return new ArtifactGraph(
                "graph-" + jobId + "-" + System.currentTimeMillis(),
                jobId,
                rootNode.id(),
                Map.of(rootNode.id(), rootNode),
                Instant.now(),
                1
        );
    }

    /**
     * Create an empty graph for a job.
     */
    public static ArtifactGraph empty(String jobId) {
        return new ArtifactGraph(
                "graph-" + jobId + "-empty",
                jobId,
                null,
                Map.of(),
                Instant.now(),
                0
        );
    }

    /**
     * Add a node to the graph.
     */
    public ArtifactGraph addNode(ArtifactNode node) {
        Map<String, ArtifactNode> newNodes = new HashMap<>(nodes);
        newNodes.put(node.id(), node);
        return new ArtifactGraph(
                graphId,
                jobId,
                rootArtifactId != null ? rootArtifactId : node.id(),
                Map.copyOf(newNodes),
                createdAt,
                version
        );
    }

    /**
     * Add multiple nodes to the graph.
     */
    public ArtifactGraph addNodes(Collection<ArtifactNode> nodesToAdd) {
        Map<String, ArtifactNode> newNodes = new HashMap<>(nodes);
        String newRootId = rootArtifactId;
        for (ArtifactNode node : nodesToAdd) {
            newNodes.put(node.id(), node);
            if (newRootId == null) {
                newRootId = node.id();
            }
        }
        return new ArtifactGraph(
                graphId,
                jobId,
                newRootId,
                Map.copyOf(newNodes),
                createdAt,
                version
        );
    }

    /**
     * Get the root artifact node.
     */
    public Optional<ArtifactNode> getRootNode() {
        return rootArtifactId != null ? Optional.ofNullable(nodes.get(rootArtifactId)) : Optional.empty();
    }

    /**
     * Get a node by ID.
     */
    public Optional<ArtifactNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Get all nodes.
     */
    public Collection<ArtifactNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Get nodes by type.
     */
    public List<ArtifactNode> getNodesByType(ArtifactNodeType type) {
        return nodes.values().stream()
                .filter(n -> n.type() == type)
                .toList();
    }

    /**
     * Get all leaf nodes (nodes with no children).
     */
    public List<ArtifactNode> getLeafNodes() {
        Set<String> parentIds = new HashSet<>();
        for (ArtifactNode node : nodes.values()) {
            parentIds.addAll(node.parentArtifactIds());
        }
        return nodes.values().stream()
                .filter(n -> !parentIds.contains(n.id()))
                .toList();
    }

    /**
     * Get children of a node.
     */
    public List<ArtifactNode> getChildren(String nodeId) {
        return nodes.values().stream()
                .filter(n -> n.parentArtifactIds().contains(nodeId))
                .toList();
    }

    /**
     * Get parents of a node.
     */
    public List<ArtifactNode> getParents(String nodeId) {
        ArtifactNode node = nodes.get(nodeId);
        if (node == null) return List.of();
        return node.parentArtifactIds().stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Check if the graph is empty.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Get the number of nodes in the graph.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Get the primary output artifact (root node).
     */
    public Optional<ArtifactNode> getPrimaryOutput() {
        return getRootNode();
    }

    /**
     * Get the primary output URI.
     */
    public Optional<String> getPrimaryOutputUri() {
        return getRootNode().map(ArtifactNode::uri);
    }

    /**
     * Check if the graph has a specific artifact type.
     */
    public boolean hasType(ArtifactNodeType type) {
        return nodes.values().stream().anyMatch(n -> n.type() == type);
    }

    /**
     * Create a new version of the graph with updated root.
     */
    public ArtifactGraph withNewVersion(ArtifactNode newRoot) {
        Map<String, ArtifactNode> newNodes = new HashMap<>(nodes);
        newNodes.put(newRoot.id(), newRoot);
        return new ArtifactGraph(
                graphId,
                jobId,
                newRoot.id(),
                Map.copyOf(newNodes),
                createdAt,
                version + 1
        );
    }

    /**
     * Convert to a flat list for storage.
     */
    public List<ArtifactNode> toList() {
        return List.copyOf(nodes.values());
    }

    /**
     * Build a graph from a list of nodes.
     */
    public static ArtifactGraph fromList(String jobId, List<ArtifactNode> nodeList) {
        if (nodeList == null || nodeList.isEmpty()) {
            return empty(jobId);
        }

        Map<String, ArtifactNode> nodeMap = new HashMap<>();
        for (ArtifactNode node : nodeList) {
            nodeMap.put(node.id(), node);
        }

        // Find root (node that is not a child of any other node)
        Set<String> childIds = new HashSet<>();
        for (ArtifactNode node : nodeList) {
            childIds.addAll(node.parentArtifactIds());
        }
        String rootId = nodeList.stream()
                .filter(n -> !childIds.contains(n.id()))
                .findFirst()
                .map(ArtifactNode::id)
                .orElse(nodeList.get(0).id());

        return new ArtifactGraph(
                "graph-" + jobId,
                jobId,
                rootId,
                Map.copyOf(nodeMap),
                Instant.now(),
                1
        );
    }
}
