package com.example.platform.render.domain.timeline.compile;

import java.util.List;
import java.util.Map;

/**
 * A single node in the artifact dependency graph.
 *
 * <p>Each node represents an intermediate or final product in the render pipeline.
 * Nodes are provider-neutral and content-addressable.</p>
 *
 * @param nodeId          deterministic node identifier
 * @param type            artifact node type
 * @param label           human-readable label
 * @param sourceAssetId   source asset ID (for INPUT_MEDIA nodes)
 * @param clipId          source clip ID (for media segment nodes)
 * @param trackId         source track ID
 * @param parameters      immutable parameters snapshot
 * @param requirements    capability requirements for this node
 */
public record ArtifactNode(
        String nodeId,
        ArtifactNodeType type,
        String label,
        String sourceAssetId,
        String clipId,
        String trackId,
        Map<String, String> parameters,
        ArtifactRequirement requirements) {

    /**
     * Creates a simple artifact node without requirements.
     */
    public static ArtifactNode of(String nodeId, ArtifactNodeType type, String label) {
        return new ArtifactNode(nodeId, type, label, null, null, null, Map.of(), null);
    }

    /**
     * Creates an input media node.
     */
    public static ArtifactNode inputMedia(String nodeId, String sourceAssetId, String clipId) {
        return new ArtifactNode(nodeId, ArtifactNodeType.INPUT_MEDIA,
                "input:" + sourceAssetId, sourceAssetId, clipId, null, Map.of(), null);
    }

    /**
     * Creates a final render node.
     */
    public static ArtifactNode finalRender(String nodeId) {
        return new ArtifactNode(nodeId, ArtifactNodeType.FINAL_RENDER,
                "final-render", null, null, null, Map.of(), null);
    }
}
