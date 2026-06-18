package com.example.platform.shared.capability;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /**
     * Represents a node in the automation flow.
     */
    public record FlowNode(
        String nodeId,
        NodeType nodeType,
        String capabilityKey,
        String capabilityVersion,
        Map<String, Object> config,
        Set<String> requiredPermissions,
        FlowNodeErrorPolicy errorPolicy
    ) {}

    /**
     * Represents an edge between two nodes.
     */
    public record FlowEdge(
        String fromNodeId,
        String toNodeId,
        String condition
    ) {}

    /**
     * Type of node in the automation flow.
     */
    public enum NodeType {
        ACTION,
        EXTENSION_POINT,
        CONDITION,
        APPROVAL,
        DELAY,
        NOTIFICATION,
        WEBHOOK,
        HOOK
    }

    /**
     * Error policy for flow nodes.
     */
    public enum FlowNodeErrorPolicy {
        FAIL, SKIP, RETRY
    }
}
