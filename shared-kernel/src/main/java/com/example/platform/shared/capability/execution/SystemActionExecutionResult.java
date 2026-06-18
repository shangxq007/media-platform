package com.example.platform.shared.capability.execution;

import com.example.platform.shared.capability.ArtifactRef;

import java.util.List;
import java.util.Map;

/**
 * Result of a system action execution.
 *
 * <p><strong>Contract only:</strong> This defines the execution result shape.
 * Runtime execution is not implemented.</p>
 */
public record SystemActionExecutionResult(
    SystemActionExecutionStatus status,
    String actionKey,
    Map<String, Object> output,
    List<ArtifactRef> artifactRefs,
    String logsRef,
    String errorCode,
    boolean retryable,
    boolean dryRun,
    Map<String, Object> metrics
) {
    public SystemActionExecutionResult {
        output = output != null ? Map.copyOf(output) : Map.of();
        artifactRefs = artifactRefs != null ? List.copyOf(artifactRefs) : List.of();
        metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
    }

    /**
     * Create a DRY_RUN_SUCCEEDED result.
     */
    public static SystemActionExecutionResult dryRunSucceeded(String actionKey) {
        return new SystemActionExecutionResult(
            SystemActionExecutionStatus.DRY_RUN_SUCCEEDED,
            actionKey,
            Map.of(),
            List.of(),
            null,
            null,
            false,
            true,
            Map.of()
        );
    }

    /**
     * Create a NOT_IMPLEMENTED result.
     */
    public static SystemActionExecutionResult notImplemented(String actionKey) {
        return new SystemActionExecutionResult(
            SystemActionExecutionStatus.NOT_IMPLEMENTED,
            actionKey,
            Map.of(),
            List.of(),
            null,
            "NOT_IMPLEMENTED",
            false,
            false,
            Map.of()
        );
    }

    /**
     * Create a VALIDATION_FAILED result.
     */
    public static SystemActionExecutionResult validationFailed(String actionKey, String errorCode) {
        return new SystemActionExecutionResult(
            SystemActionExecutionStatus.VALIDATION_FAILED,
            actionKey,
            Map.of(),
            List.of(),
            null,
            errorCode,
            false,
            false,
            Map.of()
        );
    }
}
