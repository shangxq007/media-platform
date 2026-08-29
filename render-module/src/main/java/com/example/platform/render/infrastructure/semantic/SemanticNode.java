package com.example.platform.render.infrastructure.semantic;

import java.time.Instant;
import java.util.Map;

/**
 * A semantic node in the SemanticGraph.
 * Maps UEEG nodes to human-readable semantic meaning.
 */
public record SemanticNode(
        String nodeId,
        SemanticNodeType type,
        String title,
        String description,
        String icon,
        String severity,
        Map<String, Object> data,
        Instant timestamp
) {
    /**
     * Create an execution state semantic node.
     */
    public static SemanticNode executionState(String nodeId, String fromState, String toState,
                                                String reason) {
        return new SemanticNode(
                nodeId,
                SemanticNodeType.EXECUTION,
                "System is executing your render job",
                String.format("Transitioned from %s to %s: %s", fromState, toState, reason),
                "play_arrow",
                "info",
                Map.of("fromState", fromState, "toState", toState, "reason", reason),
                Instant.now()
        );
    }

    /**
     * Create a neutral commercial admission semantic node.
     */
    public static SemanticNode commercialDecision(
            String nodeId, String decision, String reasonCode) {
        String description = "ALLOWED".equals(decision)
                ? "Commercial admission allowed"
                : "Commercial admission denied: " + reasonCode;

        return new SemanticNode(
                nodeId,
                SemanticNodeType.COMMERCIAL,
                "Commercial admission evaluation",
                description,
                "verified_user",
                "ALLOWED".equals(decision) ? "success" : "error",
                Map.of("decision", decision, "reasonCode", reasonCode),
                Instant.now()
        );
    }

    /**
     * Create a policy decision semantic node.
     */
    public static SemanticNode policyDecision(String nodeId, boolean allowed,
                                                 String denyReason, double discountPercent) {
        String description = allowed
                ? String.format("Policy constraints applied. Discount: %.1f%%", discountPercent)
                : String.format("Policy constraint violated: %s", denyReason);

        return new SemanticNode(
                nodeId,
                SemanticNodeType.POLICY,
                "Policy constraints applied",
                description,
                "policy",
                allowed ? "success" : "warning",
                Map.of("allowed", allowed, "denyReason", denyReason != null ? denyReason : "",
                        "discountPercent", discountPercent),
                Instant.now()
        );
    }

    /**
     * Create a provider decision semantic node.
     */
    public static SemanticNode providerDecision(String nodeId, String selectedProvider,
                                                   String reason, boolean fallbackTriggered) {
        String description = fallbackTriggered
                ? String.format("Execution engine selected: %s (fallback triggered: %s)", selectedProvider, reason)
                : String.format("Execution engine selected: %s", selectedProvider);

        return new SemanticNode(
                nodeId,
                SemanticNodeType.PROVIDER,
                "Execution engine selection",
                description,
                "settings",
                "info",
                Map.of("selectedProvider", selectedProvider, "reason", reason,
                        "fallbackTriggered", fallbackTriggered),
                Instant.now()
        );
    }

    /**
     * Create an artifact semantic node.
     */
    public static SemanticNode artifact(String nodeId, String artifactType,
                                           String uri, String hash) {
        return new SemanticNode(
                nodeId,
                SemanticNodeType.ARTIFACT,
                "Final output generated",
                String.format("Generated %s artifact: %s", artifactType, uri),
                "movie",
                "success",
                Map.of("artifactType", artifactType, "uri", uri, "hash", hash),
                Instant.now()
        );
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
     * Semantic node types.
     */
    public enum SemanticNodeType {
        EXECUTION,
        COMMERCIAL,
        POLICY,
        PROVIDER,
        ARTIFACT
    }
}
