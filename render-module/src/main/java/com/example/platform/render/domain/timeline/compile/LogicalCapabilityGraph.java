package com.example.platform.render.domain.timeline.compile;

import java.util.List;

/**
 * Provider-neutral logical capability graph derived from ArtifactDependencyGraph.
 *
 * <p>Each artifact node is annotated with capability requirements describing
 * what capabilities are needed to produce that artifact. This graph is
 * deterministic, provider-neutral, and feeds into ProviderBindingPlan
 * (future work).</p>
 *
 * @param graphId    deterministic graph identifier
 * @param timelineId source timeline identifier
 * @param nodes      ordered list of capability nodes
 * @param edges      list of capability edges (same topology as artifact graph)
 */
public record LogicalCapabilityGraph(
        String graphId,
        String timelineId,
        List<LogicalCapabilityNode> nodes,
        List<LogicalCapabilityEdge> edges) {

    /**
     * Returns the final render capability node.
     */
    public LogicalCapabilityNode finalRenderNode() {
        return nodes.stream()
                .filter(n -> n.artifactNodeType() == ArtifactNodeType.FINAL_RENDER)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns true if all capability requirements are resolvable.
     * v0 always returns true (no provider binding yet).
     */
    public boolean isFullyResolvable() {
        return nodes.stream().allMatch(n -> n.requirement() != null);
    }

    /**
     * Returns nodes with unresolved or unsupported capabilities.
     */
    public List<LogicalCapabilityNode> unsupportedNodes() {
        return nodes.stream()
                .filter(n -> n.requirement() != null
                        && n.requirement().requiredCapabilities().stream()
                        .anyMatch(c -> c.startsWith("UNSUPPORTED")))
                .toList();
    }
}
