package com.example.platform.render.domain.timeline.compile.binding;

import java.util.List;

/**
 * Provider-neutral binding plan that maps capability graph nodes to providers.
 *
 * <p>Internal only — not exposed in public APIs.
 * Derived deterministically from LogicalCapabilityGraph.</p>
 *
 * <p>This plan does NOT execute providers. It only records the binding decisions.</p>
 *
 * @param planId          deterministic plan identifier
 * @param capabilityGraphId source capability graph ID
 * @param timelineId      source timeline identifier
 * @param nodes           binding nodes with decisions
 * @param edges           binding edges (same topology as capability graph)
 * @param bindingMode     binding mode: PRODUCTION, MANUAL, EXPERIMENT
 * @param allBound        true if all nodes are successfully bound
 * @param hasFailures     true if any node failed binding
 */
public record ProviderBindingPlan(
        ProviderBindingPlanId planId,
        String capabilityGraphId,
        String timelineId,
        List<ProviderBindingNode> nodes,
        List<ProviderBindingEdge> edges,
        String bindingMode,
        boolean allBound,
        boolean hasFailures) {

    /**
     * Returns the final render binding node.
     */
    public ProviderBindingNode finalRenderNode() {
        return nodes.stream()
                .filter(n -> n.artifactNodeType() == com.example.platform.render.domain.timeline.compile.ArtifactNodeType.FINAL_RENDER)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns all successfully bound nodes.
     */
    public List<ProviderBindingNode> boundNodes() {
        return nodes.stream().filter(ProviderBindingNode::isBound).toList();
    }

    /**
     * Returns all failed nodes.
     */
    public List<ProviderBindingNode> failedNodes() {
        return nodes.stream().filter(ProviderBindingNode::isFailed).toList();
    }

    /**
     * Returns all binding decisions.
     */
    public List<ProviderBindingDecision> allDecisions() {
        return nodes.stream().map(ProviderBindingNode::decision).toList();
    }

    /**
     * Returns a summary of binding decisions for logging/debugging.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProviderBindingPlan[").append(planId).append("] ");
        sb.append("mode=").append(bindingMode);
        sb.append(" nodes=").append(nodes.size());
        sb.append(" bound=").append(boundNodes().size());
        sb.append(" failed=").append(failedNodes().size());
        sb.append(" allBound=").append(allBound);
        return sb.toString();
    }
}
