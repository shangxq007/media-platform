package com.example.platform.shared.capability;

import java.util.List;
import java.util.Map;

/**
 * Represents a planned automation flow model.
 *
 * <p>AutomationFlow defines a config-only workflow that chains system actions
 * based on triggers.</p>
 *
 * <p><strong>Contract only:</strong> This defines the flow model shape.
 * No execution engine is implemented.</p>
 */
public record AutomationFlow(
    String flowId,
    String tenantId,
    String name,
    AutomationTrigger trigger,
    List<FlowNode> nodes,
    List<FlowEdge> edges,
    FlowStatus status,
    int version
) {
    public record FlowNode(
        String nodeId,
        String actionKey,
        Map<String, String> inputMapping,
        FlowNodeErrorPolicy errorPolicy
    ) {}

    public record FlowEdge(
        String fromNodeId,
        String toNodeId,
        String condition
    ) {}

    public enum FlowNodeErrorPolicy {
        FAIL, SKIP, RETRY
    }
}
