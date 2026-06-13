package com.example.platform.render.infrastructure.unified;

import java.time.Instant;

/**
 * An edge in the Unified Execution & Economy Graph.
 * 
 * <p>Edges represent causal relationships between nodes.
 * They ensure deterministic ordering and traceability.
 */
public record GraphEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        String edgeType,
        Instant timestamp,
        String description
) {
    /**
     * Create a causal link between two nodes.
     */
    public static GraphEdge link(String sourceNodeId, String targetNodeId, String edgeType) {
        return new GraphEdge(
                "edge-" + sourceNodeId + "-" + targetNodeId,
                sourceNodeId,
                targetNodeId,
                edgeType,
                Instant.now(),
                null
        );
    }

    /**
     * Create a causal link with description.
     */
    public static GraphEdge linkWithDescription(String sourceNodeId, String targetNodeId,
                                                   String edgeType, String description) {
        return new GraphEdge(
                "edge-" + sourceNodeId + "-" + targetNodeId,
                sourceNodeId,
                targetNodeId,
                edgeType,
                Instant.now(),
                description
        );
    }

    /**
     * Edge types for different relationships.
     */
    public static final String TRIGGERS = "TRIGGERS";
    public static final String DEPENDS_ON = "DEPENDS_ON";
    public static final String PRODUCES = "PRODUCES";
    public static final String CONSUMES = "CONSUMES";
    public static final String DECIDES = "DECIDES";
    public static final String VALIDATES = "VALIDATES";
    public static final String ROLLS_BACK = "ROLLS_BACK";
}
