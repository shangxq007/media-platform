package com.example.platform.render.domain.timeline.compile.binding;

import com.example.platform.render.domain.timeline.compile.ArtifactEdgeType;

/**
 * A directed edge in the provider binding plan.
 *
 * <p>Edges mirror the capability graph topology.</p>
 *
 * @param edgeId        deterministic edge identifier
 * @param sourceNodeId  the node that depends (downstream)
 * @param targetNodeId  the node that is depended upon (upstream)
 * @param type          the type of dependency relationship
 */
public record ProviderBindingEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        ArtifactEdgeType type) {

    /**
     * Creates an edge from source to target with DERIVES_FROM type.
     */
    public static ProviderBindingEdge derivesFrom(String sourceNodeId, String targetNodeId) {
        return new ProviderBindingEdge(
                sourceNodeId + "->" + targetNodeId,
                sourceNodeId, targetNodeId, ArtifactEdgeType.DERIVES_FROM);
    }

    /**
     * Creates an edge from source to target with REQUIRES_INPUT type.
     */
    public static ProviderBindingEdge requiresInput(String sourceNodeId, String targetNodeId) {
        return new ProviderBindingEdge(
                sourceNodeId + "->" + targetNodeId,
                sourceNodeId, targetNodeId, ArtifactEdgeType.REQUIRES_INPUT);
    }

    /**
     * Creates an edge from source to target with ENCODES_TO type.
     */
    public static ProviderBindingEdge encodesTo(String sourceNodeId, String targetNodeId) {
        return new ProviderBindingEdge(
                sourceNodeId + "->" + targetNodeId,
                sourceNodeId, targetNodeId, ArtifactEdgeType.ENCODES_TO);
    }

    /**
     * Creates an edge from source to target with PRODUCES type.
     */
    public static ProviderBindingEdge produces(String sourceNodeId, String targetNodeId) {
        return new ProviderBindingEdge(
                sourceNodeId + "->" + targetNodeId,
                sourceNodeId, targetNodeId, ArtifactEdgeType.PRODUCES);
    }
}
