package com.example.platform.render.infrastructure.unified;

import java.time.Instant;
import java.util.Map;

/**
 * A node in the Unified Execution & Economy Graph.
 * 
 * <p>Each node represents a decision or artifact from a subsystem.
 * Nodes are immutable and causally linked via edges.
 */
public record GraphNode(
        String nodeId,
        UnifiedRequestGraph.NodeType type,
        String subsystem,
        String action,
        String status,
        Map<String, Object> data,
        Instant timestamp,
        Map<String, Object> metadata
) {
    /**
     * Create an execution state node.
     */
    public static GraphNode executionState(String nodeId, String fromState, String toState,
                                             String reason, String triggeredBy) {
        return new GraphNode(
                nodeId,
                UnifiedRequestGraph.NodeType.EXECUTION_STATE_NODE,
                "StateMachine",
                "STATE_TRANSITION",
                "SUCCESS",
                Map.of(
                        "fromState", fromState,
                        "toState", toState,
                        "reason", reason,
                        "triggeredBy", triggeredBy
                ),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a billing decision node.
     */
    public static GraphNode billingDecision(String nodeId, String decision, String reasonCode,
                                              String reasonMessage, Double estimatedCost) {
        return new GraphNode(
                nodeId,
                UnifiedRequestGraph.NodeType.BILLING_DECISION_NODE,
                "BillingDecisionEngine",
                "BILLING_DECISION",
                decision,
                Map.of(
                        "reasonCode", reasonCode,
                        "reasonMessage", reasonMessage,
                        "estimatedCost", estimatedCost != null ? estimatedCost : 0.0
                ),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a policy decision node.
     */
    public static GraphNode policyDecision(String nodeId, boolean allowed, String denyReason,
                                             double discountPercent, double multiplier) {
        return new GraphNode(
                nodeId,
                UnifiedRequestGraph.NodeType.POLICY_DECISION_NODE,
                "PolicyEngine",
                "POLICY_EVALUATION",
                allowed ? "ALLOWED" : "DENIED",
                Map.of(
                        "allowed", allowed,
                        "denyReason", denyReason != null ? denyReason : "",
                        "discountPercent", discountPercent,
                        "multiplier", multiplier
                ),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a provider decision node.
     */
    public static GraphNode providerDecision(String nodeId, String selectedProvider,
                                               String provider, String reason,
                                               boolean fallbackTriggered) {
        return new GraphNode(
                nodeId,
                UnifiedRequestGraph.NodeType.PROVIDER_DECISION_NODE,
                "ProviderRuntimeEngine",
                "PROVIDER_SELECTION",
                "SELECTED",
                Map.of(
                        "selectedProvider", selectedProvider,
                        "provider", provider,
                        "reason", reason,
                        "fallbackTriggered", fallbackTriggered
                ),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create an artifact node.
     */
    public static GraphNode artifact(String nodeId, String artifactId, String artifactType,
                                       String uri, String hash) {
        return new GraphNode(
                nodeId,
                UnifiedRequestGraph.NodeType.ARTIFACT_NODE,
                "ArtifactGraph",
                "ARTIFACT_CREATED",
                "SUCCESS",
                Map.of(
                        "artifactId", artifactId,
                        "artifactType", artifactType,
                        "uri", uri,
                        "hash", hash
                ),
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Get a data value.
     */
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * Get a data value as string.
     */
    public String getStringData(String key, String defaultValue) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Get a data value as double.
     */
    public double getDoubleData(String key, double defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    /**
     * Get a data value as boolean.
     */
    public boolean getBooleanData(String key, boolean defaultValue) {
        Object val = data.get(key);
        if (val instanceof Boolean b) return b;
        return defaultValue;
    }

    /**
     * Check if this node represents a success.
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status) || "ALLOWED".equals(status) || "SELECTED".equals(status);
    }

    /**
     * Check if this node represents a failure.
     */
    public boolean isFailure() {
        return "FAILED".equals(status) || "DENIED".equals(status);
    }

    /**
     * Get a human-readable description.
     */
    public String getDescription() {
        return String.format("[%s] %s.%s: %s (%s)",
                nodeId, subsystem, action, status, data);
    }
}
