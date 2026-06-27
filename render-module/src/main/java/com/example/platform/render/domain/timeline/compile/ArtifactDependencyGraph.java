package com.example.platform.render.domain.timeline.compile;

import java.util.List;

/**
 * Provider-neutral artifact dependency graph derived from NormalizedTimeline.
 *
 * <p>Describes what intermediate products (artifacts) are needed to produce
 * the final output, and their dependency relationships. This graph is
 * deterministic, acyclic, and provider-neutral.</p>
 *
 * <p>This is NOT ProductDependency — it is a planning graph for compile.
 * It does not replace ProductRuntime or StorageRuntime.</p>
 *
 * @param graphId    deterministic graph identifier
 * @param timelineId source timeline identifier
 * @param nodes      ordered list of artifact nodes
 * @param edges      list of dependency edges
 */
public record ArtifactDependencyGraph(
        String graphId,
        String timelineId,
        List<ArtifactNode> nodes,
        List<ArtifactEdge> edges) {

    /**
     * Returns the final render node (root of the DAG).
     */
    public ArtifactNode finalRenderNode() {
        return nodes.stream()
                .filter(n -> n.type() == ArtifactNodeType.FINAL_RENDER)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns all edges where the given node is the source (downstream dependencies).
     */
    public List<ArtifactEdge> edgesFrom(String nodeId) {
        return edges.stream()
                .filter(e -> e.sourceNodeId().equals(nodeId))
                .toList();
    }

    /**
     * Returns all edges where the given node is the target (upstream dependencies).
     */
    public List<ArtifactEdge> edgesTo(String nodeId) {
        return edges.stream()
                .filter(e -> e.targetNodeId().equals(nodeId))
                .toList();
    }

    /**
     * Returns true if the graph is empty (no nodes).
     */
    public boolean isEmpty() {
        return nodes == null || nodes.isEmpty();
    }
}
