package com.example.platform.render.infrastructure.semantic;

/**
 * A semantic edge in the SemanticGraph.
 * Represents causal relationships between semantic nodes.
 */
public record SemanticEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        String edgeType,
        String description
) {
    /**
     * Create a semantic edge.
     */
    public static SemanticEdge create(String sourceNodeId, String targetNodeId,
                                         String edgeType, String description) {
        return new SemanticEdge(
                "edge-" + sourceNodeId + "-" + targetNodeId,
                sourceNodeId,
                targetNodeId,
                edgeType,
                description
        );
    }

    /**
     * Edge types for semantic relationships.
     */
    public static final String TRIGGERS = "triggers";
    public static final String DEPENDS_ON = "depends_on";
    public static final String PRODUCES = "produces";
    public static final String EXPLAINS = "explains";
}
