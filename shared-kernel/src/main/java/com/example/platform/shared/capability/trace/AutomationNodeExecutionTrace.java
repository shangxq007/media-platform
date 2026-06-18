package com.example.platform.shared.capability.trace;

import com.example.platform.shared.capability.ArtifactRef;
import com.example.platform.shared.capability.AutomationFlow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents the execution trace of a single node in an automation flow.
 *
 * <p><strong>Contract only:</strong> This defines the node trace shape.
 * Persistence is not implemented.</p>
 */
public record AutomationNodeExecutionTrace(
    String nodeId,
    AutomationFlow.NodeType nodeType,
    String capabilityKey,
    String capabilityVersion,
    AutomationNodeExecutionTraceStatus status,
    Instant startedAt,
    Instant completedAt,
    int attemptCount,
    List<AutomationNodeExecutionAttempt> attempts,
    Map<String, Object> output,
    List<ArtifactRef> artifactRefs,
    String errorCode,
    boolean retryable,
    String logsRef
) {
    public AutomationNodeExecutionTrace {
        attempts = attempts != null ? List.copyOf(attempts) : List.of();
        output = output != null ? Map.copyOf(output) : Map.of();
        artifactRefs = artifactRefs != null ? List.copyOf(artifactRefs) : List.of();
    }

    /**
     * Create a DRY_RUN_SUCCEEDED node trace.
     */
    public static AutomationNodeExecutionTrace dryRunSucceeded(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey) {
        Instant now = Instant.now();
        return new AutomationNodeExecutionTrace(
            nodeId,
            nodeType,
            capabilityKey,
            null,
            AutomationNodeExecutionTraceStatus.DRY_RUN_SUCCEEDED,
            now,
            now,
            0,
            List.of(),
            Map.of(),
            List.of(),
            null,
            false,
            null
        );
    }

    /**
     * Create a NOT_IMPLEMENTED node trace.
     */
    public static AutomationNodeExecutionTrace notImplemented(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String errorCode) {
        Instant now = Instant.now();
        return new AutomationNodeExecutionTrace(
            nodeId,
            nodeType,
            capabilityKey,
            null,
            AutomationNodeExecutionTraceStatus.NOT_IMPLEMENTED,
            now,
            now,
            0,
            List.of(),
            Map.of(),
            List.of(),
            errorCode,
            false,
            null
        );
    }

    /**
     * Create a SKIPPED node trace.
     */
    public static AutomationNodeExecutionTrace skipped(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey) {
        Instant now = Instant.now();
        return new AutomationNodeExecutionTrace(
            nodeId,
            nodeType,
            capabilityKey,
            null,
            AutomationNodeExecutionTraceStatus.SKIPPED,
            now,
            now,
            0,
            List.of(),
            Map.of(),
            List.of(),
            null,
            false,
            null
        );
    }

    /**
     * Create a VALIDATION_FAILED node trace.
     */
    public static AutomationNodeExecutionTrace validationFailed(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String errorCode) {
        Instant now = Instant.now();
        return new AutomationNodeExecutionTrace(
            nodeId,
            nodeType,
            capabilityKey,
            null,
            AutomationNodeExecutionTraceStatus.VALIDATION_FAILED,
            now,
            now,
            0,
            List.of(),
            Map.of(),
            List.of(),
            errorCode,
            false,
            null
        );
    }

    /**
     * Create a FAILED node trace.
     */
    public static AutomationNodeExecutionTrace failed(String nodeId, AutomationFlow.NodeType nodeType, String capabilityKey, String errorCode, boolean retryable) {
        Instant now = Instant.now();
        return new AutomationNodeExecutionTrace(
            nodeId,
            nodeType,
            capabilityKey,
            null,
            AutomationNodeExecutionTraceStatus.FAILED,
            now,
            now,
            1,
            List.of(),
            Map.of(),
            List.of(),
            errorCode,
            retryable,
            null
        );
    }
}
