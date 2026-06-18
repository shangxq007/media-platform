package com.example.platform.shared.capability.flow;

import com.example.platform.shared.capability.validation.AutomationFlowValidationResult;

import java.time.Instant;
import java.util.List;

/**
 * Result of an automation flow dry-run execution.
 *
 * <p><strong>Contract only:</strong> This defines the dry-run result shape.
 * Runtime execution is not implemented.</p>
 */
public record AutomationFlowDryRunResult(
    AutomationFlowDryRunStatus status,
    AutomationFlowValidationResult validationResult,
    List<AutomationNodeDryRunResult> nodeResults,
    Instant startedAt,
    Instant completedAt,
    boolean dryRun
) {
    public AutomationFlowDryRunResult {
        nodeResults = nodeResults != null ? List.copyOf(nodeResults) : List.of();
    }

    /**
     * Create a VALIDATION_FAILED result.
     */
    public static AutomationFlowDryRunResult validationFailed(AutomationFlowValidationResult validationResult) {
        Instant now = Instant.now();
        return new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.VALIDATION_FAILED,
            validationResult,
            List.of(),
            now,
            now,
            true
        );
    }

    /**
     * Create a SUCCEEDED result.
     */
    public static AutomationFlowDryRunResult succeeded(AutomationFlowValidationResult validationResult, List<AutomationNodeDryRunResult> nodeResults, Instant startedAt) {
        Instant now = Instant.now();
        return new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.SUCCEEDED,
            validationResult,
            nodeResults,
            startedAt,
            now,
            true
        );
    }

    /**
     * Create a PARTIALLY_SUPPORTED result.
     */
    public static AutomationFlowDryRunResult partiallySupported(AutomationFlowValidationResult validationResult, List<AutomationNodeDryRunResult> nodeResults, Instant startedAt) {
        Instant now = Instant.now();
        return new AutomationFlowDryRunResult(
            AutomationFlowDryRunStatus.PARTIALLY_SUPPORTED,
            validationResult,
            nodeResults,
            startedAt,
            now,
            true
        );
    }

    /**
     * Check if all nodes succeeded.
     */
    public boolean allNodesSucceeded() {
        return nodeResults.stream()
            .allMatch(r -> r.status() == AutomationNodeDryRunStatus.DRY_RUN_SUCCEEDED);
    }

    /**
     * Check if any nodes are not implemented.
     */
    public boolean hasNotImplementedNodes() {
        return nodeResults.stream()
            .anyMatch(r -> r.status() == AutomationNodeDryRunStatus.NOT_IMPLEMENTED);
    }
}
