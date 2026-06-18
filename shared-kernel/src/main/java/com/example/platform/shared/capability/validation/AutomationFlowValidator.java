package com.example.platform.shared.capability.validation;

import com.example.platform.shared.capability.*;
import com.example.platform.shared.capability.registry.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for automation flow definitions.
 *
 * <p>AutomationFlowValidator verifies that a flow is structurally valid
 * and references registered capabilities. This is validation only,
 * not runtime execution.</p>
 *
 * <p><strong>Contract only:</strong> This defines the validator shape.
 * Runtime execution is not implemented.</p>
 */
public class AutomationFlowValidator {

    private final SystemActionRegistry actionRegistry;
    private final ExtensionPointRegistry extensionPointRegistry;
    private final EventTypeRegistry eventTypeRegistry;
    private final HookPointRegistry hookPointRegistry;

    /**
     * Create a validator with the given registries.
     *
     * @param actionRegistry the system action registry
     * @param extensionPointRegistry the extension point registry
     * @param eventTypeRegistry the event type registry
     * @param hookPointRegistry the hook point registry
     */
    public AutomationFlowValidator(
        SystemActionRegistry actionRegistry,
        ExtensionPointRegistry extensionPointRegistry,
        EventTypeRegistry eventTypeRegistry,
        HookPointRegistry hookPointRegistry
    ) {
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry must not be null");
        this.extensionPointRegistry = Objects.requireNonNull(extensionPointRegistry, "extensionPointRegistry must not be null");
        this.eventTypeRegistry = Objects.requireNonNull(eventTypeRegistry, "eventTypeRegistry must not be null");
        this.hookPointRegistry = Objects.requireNonNull(hookPointRegistry, "hookPointRegistry must not be null");
    }

    /**
     * Validate an automation flow.
     *
     * @param flow the flow to validate
     * @return the validation result
     */
    public AutomationFlowValidationResult validate(AutomationFlow flow) {
        if (flow == null) {
            return AutomationFlowValidationResult.failure(
                AutomationFlowValidationIssue.error(
                    AutomationFlowValidationCode.FLOW_ID_MISSING,
                    "Flow must not be null"
                )
            );
        }

        List<AutomationFlowValidationIssue> issues = new ArrayList<>();

        // Validate basic fields
        validateBasicFields(flow, issues);

        // Validate trigger
        validateTrigger(flow.trigger(), issues);

        // Validate nodes
        validateNodes(flow.nodes(), issues);

        // Validate edges
        validateEdges(flow.edges(), flow.nodes(), issues);

        // Validate graph structure
        validateGraphStructure(flow.nodes(), flow.edges(), issues);

        // Check if there are any errors (not just warnings/info)
        boolean hasErrors = issues.stream()
            .anyMatch(i -> i.severity() == AutomationFlowValidationSeverity.ERROR);

        if (hasErrors) {
            return AutomationFlowValidationResult.failure(issues);
        }

        // Return success with warnings if present
        return AutomationFlowValidationResult.success(issues);
    }

    /**
     * Validate basic fields of the flow.
     */
    private void validateBasicFields(AutomationFlow flow, List<AutomationFlowValidationIssue> issues) {
        if (flow.flowId() == null || flow.flowId().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.FLOW_ID_MISSING,
                "Flow ID must not be blank"
            ));
        }

        if (flow.tenantId() == null || flow.tenantId().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.TENANT_ID_MISSING,
                "Tenant ID must not be blank"
            ));
        }

        if (flow.nodes() == null || flow.nodes().isEmpty()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_MISSING,
                "Flow must have at least one node"
            ));
        }
    }

    /**
     * Validate the trigger.
     */
    private void validateTrigger(AutomationTrigger trigger, List<AutomationFlowValidationIssue> issues) {
        if (trigger == null) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.TRIGGER_MISSING,
                "Trigger must not be null"
            ));
            return;
        }

        // If trigger is EVENT type, validate event type reference
        if (trigger.type() == AutomationTrigger.TriggerType.EVENT) {
            if (trigger.eventType() != null && !trigger.eventType().isBlank()) {
                boolean found = eventTypeRegistry.find(trigger.eventType(), trigger.eventVersion()).isPresent();
                if (!found) {
                    issues.add(AutomationFlowValidationIssue.error(
                        AutomationFlowValidationCode.TRIGGER_REFERENCES_UNKNOWN_EVENT_TYPE,
                        "Trigger references unknown event type: " + trigger.eventType()
                    ));
                }
            }
        }
    }

    /**
     * Validate nodes in the flow.
     */
    private void validateNodes(List<AutomationFlow.FlowNode> nodes, List<AutomationFlowValidationIssue> issues) {
        if (nodes == null) {
            return;
        }

        for (AutomationFlow.FlowNode node : nodes) {
            validateNode(node, issues);
        }
    }

    /**
     * Validate a single node.
     */
    private void validateNode(AutomationFlow.FlowNode node, List<AutomationFlowValidationIssue> issues) {
        if (node.nodeId() == null || node.nodeId().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_MISSING,
                "Node ID must not be blank"
            ));
            return;
        }

        // Validate node type
        if (node.nodeType() == null) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.UNSUPPORTED_NODE_TYPE,
                "Node type must not be null",
                node.nodeId()
            ));
            return;
        }

        // Validate capability reference based on node type
        switch (node.nodeType()) {
            case ACTION:
                validateActionNode(node, issues);
                break;
            case EXTENSION_POINT:
                validateExtensionPointNode(node, issues);
                break;
            case HOOK:
                validateHookNode(node, issues);
                break;
            case CONDITION:
            case APPROVAL:
            case DELAY:
            case NOTIFICATION:
            case WEBHOOK:
                // These are allowed but don't require capability lookup
                break;
            default:
                issues.add(AutomationFlowValidationIssue.error(
                    AutomationFlowValidationCode.UNSUPPORTED_NODE_TYPE,
                    "Unsupported node type: " + node.nodeType(),
                    node.nodeId()
                ));
        }
    }

    /**
     * Validate an ACTION node references a registered SystemAction.
     */
    private void validateActionNode(AutomationFlow.FlowNode node, List<AutomationFlowValidationIssue> issues) {
        if (node.capabilityKey() == null || node.capabilityKey().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_ACTION,
                "ACTION node must have a capability key",
                node.nodeId()
            ));
            return;
        }

        boolean found = actionRegistry.findByKey(node.capabilityKey()).isPresent();
        if (!found) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_ACTION,
                "ACTION node references unknown action: " + node.capabilityKey(),
                node.nodeId()
            ));
        }
    }

    /**
     * Validate an EXTENSION_POINT node references a registered ExtensionPoint.
     */
    private void validateExtensionPointNode(AutomationFlow.FlowNode node, List<AutomationFlowValidationIssue> issues) {
        if (node.capabilityKey() == null || node.capabilityKey().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_EXTENSION_POINT,
                "EXTENSION_POINT node must have a capability key",
                node.nodeId()
            ));
            return;
        }

        boolean found = extensionPointRegistry.find(node.capabilityKey(), node.capabilityVersion()).isPresent();
        if (!found) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.NODE_REFERENCES_UNKNOWN_EXTENSION_POINT,
                "EXTENSION_POINT node references unknown extension point: " + node.capabilityKey(),
                node.nodeId()
            ));
        }
    }

    /**
     * Validate a HOOK node references a registered HookPoint.
     */
    private void validateHookNode(AutomationFlow.FlowNode node, List<AutomationFlowValidationIssue> issues) {
        if (node.capabilityKey() == null || node.capabilityKey().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.HOOK_REFERENCES_UNKNOWN_HOOK_POINT,
                "HOOK node must have a capability key",
                node.nodeId()
            ));
            return;
        }

        // Try to find hook point by key (any phase)
        boolean found = hookPointRegistry.list().stream()
            .anyMatch(hp -> hp.key().equals(node.capabilityKey()));
        if (!found) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.HOOK_REFERENCES_UNKNOWN_HOOK_POINT,
                "HOOK node references unknown hook point: " + node.capabilityKey(),
                node.nodeId()
            ));
        }
    }

    /**
     * Validate edges in the flow.
     */
    private void validateEdges(List<AutomationFlow.FlowEdge> edges, List<AutomationFlow.FlowNode> nodes, List<AutomationFlowValidationIssue> issues) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        Set<String> nodeIds = nodes.stream()
            .map(AutomationFlow.FlowNode::nodeId)
            .collect(Collectors.toSet());

        for (AutomationFlow.FlowEdge edge : edges) {
            validateEdge(edge, nodeIds, issues);
        }
    }

    /**
     * Validate a single edge.
     */
    private void validateEdge(AutomationFlow.FlowEdge edge, Set<String> nodeIds, List<AutomationFlowValidationIssue> issues) {
        if (edge.fromNodeId() == null || edge.fromNodeId().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.EDGE_INVALID,
                "Edge fromNodeId must not be blank"
            ));
            return;
        }

        if (edge.toNodeId() == null || edge.toNodeId().isBlank()) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.EDGE_INVALID,
                "Edge toNodeId must not be blank"
            ));
            return;
        }

        if (!nodeIds.contains(edge.fromNodeId())) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.EDGE_INVALID,
                "Edge references unknown fromNodeId: " + edge.fromNodeId()
            ));
        }

        if (!nodeIds.contains(edge.toNodeId())) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.EDGE_INVALID,
                "Edge references unknown toNodeId: " + edge.toNodeId()
            ));
        }
    }

    /**
     * Validate graph structure (cycles, disconnected nodes).
     */
    private void validateGraphStructure(List<AutomationFlow.FlowNode> nodes, List<AutomationFlow.FlowEdge> edges, List<AutomationFlowValidationIssue> issues) {
        if (nodes == null || nodes.isEmpty() || edges == null || edges.isEmpty()) {
            return;
        }

        // Build adjacency list
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (AutomationFlow.FlowNode node : nodes) {
            adjacency.put(node.nodeId(), new HashSet<>());
        }
        for (AutomationFlow.FlowEdge edge : edges) {
            if (adjacency.containsKey(edge.fromNodeId()) && adjacency.containsKey(edge.toNodeId())) {
                adjacency.get(edge.fromNodeId()).add(edge.toNodeId());
            }
        }

        // Check for cycles using DFS
        if (hasCycle(adjacency)) {
            issues.add(AutomationFlowValidationIssue.error(
                AutomationFlowValidationCode.CYCLE_DETECTED,
                "Flow graph contains a cycle"
            ));
        }

        // Check for disconnected nodes
        Set<String> connectedNodes = findConnectedNodes(adjacency, nodes.get(0).nodeId());
        for (AutomationFlow.FlowNode node : nodes) {
            if (!connectedNodes.contains(node.nodeId())) {
                issues.add(AutomationFlowValidationIssue.warning(
                    AutomationFlowValidationCode.DISCONNECTED_NODE,
                    "Node is disconnected from the flow graph",
                    node.nodeId()
                ));
            }
        }
    }

    /**
     * Check if the graph has a cycle using DFS.
     */
    private boolean hasCycle(Map<String, Set<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : adjacency.keySet()) {
            if (hasCycleDFS(adjacency, node, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * DFS helper for cycle detection.
     */
    private boolean hasCycleDFS(Map<String, Set<String>> adjacency, String node, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        Set<String> neighbors = adjacency.getOrDefault(node, Set.of());
        for (String neighbor : neighbors) {
            if (hasCycleDFS(adjacency, neighbor, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * Find all connected nodes from a starting node using BFS.
     */
    private Set<String> findConnectedNodes(Map<String, Set<String>> adjacency, String startNodeId) {
        Set<String> connected = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(startNodeId);
        connected.add(startNodeId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbors = adjacency.getOrDefault(current, Set.of());
            for (String neighbor : neighbors) {
                if (!connected.contains(neighbor)) {
                    connected.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return connected;
    }
}
