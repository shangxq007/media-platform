package com.example.platform.render.domain.timeline.compile;

/**
 * A directed edge in the artifact dependency graph.
 *
 * <p>Edges represent dependency relationships between artifact nodes.
 * The graph must be acyclic (DAG).</p>
 *
 * @param edgeId        deterministic edge identifier
 * @param sourceNodeId  the node that depends (downstream)
 * @param targetNodeId  the node that is depended upon (upstream)
 * @param type          the type of dependency relationship
 */
public record ArtifactEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        ArtifactEdgeType type) {

    /**
     * Creates a DERIVES_FROM edge.
     */
    public static ArtifactEdge derivesFrom(String sourceNodeId, String targetNodeId) {
        String edgeId = sourceNodeId + "->" + targetNodeId;
        return new ArtifactEdge(edgeId, sourceNodeId, targetNodeId, ArtifactEdgeType.DERIVES_FROM);
    }

    /**
     * Creates a REQUIRES_INPUT edge.
     */
    public static ArtifactEdge requiresInput(String sourceNodeId, String targetNodeId) {
        String edgeId = sourceNodeId + "->" + targetNodeId;
        return new ArtifactEdge(edgeId, sourceNodeId, targetNodeId, ArtifactEdgeType.REQUIRES_INPUT);
    }

    /**
     * Creates an ENCODES_TO edge.
     */
    public static ArtifactEdge encodesTo(String sourceNodeId, String targetNodeId) {
        String edgeId = sourceNodeId + "->" + targetNodeId;
        return new ArtifactEdge(edgeId, sourceNodeId, targetNodeId, ArtifactEdgeType.ENCODES_TO);
    }

    /**
     * Creates a PRODUCES edge.
     */
    public static ArtifactEdge produces(String sourceNodeId, String targetNodeId) {
        String edgeId = sourceNodeId + "->" + targetNodeId;
        return new ArtifactEdge(edgeId, sourceNodeId, targetNodeId, ArtifactEdgeType.PRODUCES);
    }
}
