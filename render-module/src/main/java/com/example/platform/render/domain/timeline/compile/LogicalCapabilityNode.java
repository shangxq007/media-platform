package com.example.platform.render.domain.timeline.compile;

import java.util.Map;

/**
 * A node in the logical capability graph.
 *
 * <p>Each node represents a capability requirement for a specific artifact
 * in the compile pipeline. The node is provider-neutral and describes
 * what capabilities are needed, not which provider should fulfill them.</p>
 *
 * @param nodeId           deterministic node identifier (matches artifact node)
 * @param artifactNodeType the type of artifact this node represents
 * @param label            human-readable label
 * @param requirement      the capability requirement for this node
 * @param parameters       immutable parameters from the artifact node
 */
public record LogicalCapabilityNode(
        String nodeId,
        ArtifactNodeType artifactNodeType,
        String label,
        ArtifactRequirement requirement,
        Map<String, String> parameters) {

    /**
     * Creates a capability node from an artifact node.
     */
    public static LogicalCapabilityNode fromArtifact(ArtifactNode artifactNode) {
        return new LogicalCapabilityNode(
                artifactNode.nodeId(),
                artifactNode.type(),
                artifactNode.label(),
                artifactNode.requirements(),
                artifactNode.parameters());
    }

    /**
     * Creates a capability node with explicit requirement.
     */
    public static LogicalCapabilityNode of(String nodeId, ArtifactNodeType type,
                                             String label, ArtifactRequirement requirement) {
        return new LogicalCapabilityNode(nodeId, type, label, requirement, java.util.Map.of());
    }
}
