package com.example.platform.render.domain.timeline.compile;

/**
 * A directed edge in the logical capability graph.
 *
 * <p>Edges mirror the artifact dependency graph topology.
 * They represent capability flow dependencies.</p>
 *
 * @param edgeId        deterministic edge identifier
 * @param sourceNodeId  the node that depends (downstream)
 * @param targetNodeId  the node that is depended upon (upstream)
 * @param type          the type of capability relationship
 */
public record LogicalCapabilityEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        ArtifactEdgeType type) {

    /**
     * Creates an edge from an artifact edge.
     */
    public static LogicalCapabilityEdge fromArtifactEdge(ArtifactEdge edge) {
        return new LogicalCapabilityEdge(
                edge.edgeId(), edge.sourceNodeId(), edge.targetNodeId(), edge.type());
    }
}
