package com.example.platform.shared.capability.flow;

import com.example.platform.shared.capability.AutomationFlow;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a single node in an automation flow dry-run execution.
 *
 * <p><strong>Contract only:</strong> This defines the node result shape.
 * Runtime execution is not implemented.</p>
 */
public record AutomationNodeDryRunResult(
    String nodeId,
    AutomationFlow.NodeType nodeType,
    String capabilityKey,
    AutomationNodeDryRunStatus status,
    Map<String, Object> output,
    String errorCode,
    String message,
    Instant startedAt,
    Instant completedAt
) {
    public AutomationNodeDryRunResult {
        output = output != null ? Map.copyOf(output) : Map.of();
    }

    /**
     * Create a DRY_RUN_SUCCEEDED result.
     */
    public static AutomationNodeDryRunResult dryRunSucceeded(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey) {
        Instant now = Instant.now();
        return new AutomationNodeDryRunResult(
            nodeId,
            nodeType,
            capabilityKey,
            AutomationNodeDryRunStatus.DRY_RUN_SUCCEEDED,
            Map.of(),
            null,
            "Dry-run succeeded",
            now,
            now
        );
    }

    /**
     * Create a NOT_IMPLEMENTED result.
     */
    public static AutomationNodeDryRunResult notImplemented(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String message) {
        Instant now = Instant.now();
        return new AutomationNodeDryRunResult(
            nodeId,
            nodeType,
            capabilityKey,
            AutomationNodeDryRunStatus.NOT_IMPLEMENTED,
            Map.of(),
            "NOT_IMPLEMENTED",
            message,
            now,
            now
        );
    }

    /**
     * Create a SKIPPED result.
     */
    public static AutomationNodeDryRunResult skipped(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String message) {
        Instant now = Instant.now();
        return new AutomationNodeDryRunResult(
            nodeId,
            nodeType,
            capabilityKey,
            AutomationNodeDryRunStatus.SKIPPED,
            Map.of(),
            "SKIPPED",
            message,
            now,
            now
        );
    }

    /**
     * Create a VALIDATION_FAILED result.
     */
    public static AutomationNodeDryRunResult validationFailed(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String errorCode, String message) {
        Instant now = Instant.now();
        return new AutomationNodeDryRunResult(
            nodeId,
            nodeType,
            capabilityKey,
            AutomationNodeDryRunStatus.VALIDATION_FAILED,
            Map.of(),
            errorCode,
            message,
            now,
            now
        );
    }

    /**
     * Create a FAILED result.
     */
    public static AutomationNodeDryRunResult failed(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String errorCode, String message) {
        Instant now = Instant.now();
        return new AutomationNodeDryRunResult(
            nodeId,
            nodeType,
            capabilityKey,
            AutomationNodeDryRunStatus.FAILED,
            Map.of(),
            errorCode,
            message,
            now,
            now
        );
    }
}
