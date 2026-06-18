package com.example.platform.shared.capability.trace;

import com.example.platform.shared.capability.AutomationFlow;
import com.example.platform.shared.capability.AutomationTrigger;
import com.example.platform.shared.capability.flow.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper from dry-run results to execution traces.
 *
 * <p>AutomationDryRunTraceMapper converts AutomationFlowDryRunResult into
 * AutomationExecutionTrace for explain-plan display.</p>
 *
 * <p><strong>Contract only:</strong> This is a utility mapper.
 * No persistence or side effects.</p>
 */
public final class AutomationDryRunTraceMapper {

    private AutomationDryRunTraceMapper() {
        // Utility class
    }

    /**
     * Map a dry-run result to an execution trace.
     *
     * @param dryRunResult the dry-run result
     * @param flowId the flow ID
     * @param flowVersion the flow version
     * @param tenantId the tenant ID
     * @param triggerType the trigger type
     * @param triggerRef the trigger reference
     * @return the execution trace
     */
    public static AutomationExecutionTrace map(
        AutomationFlowDryRunResult dryRunResult,
        String flowId,
        String flowVersion,
        String tenantId,
        AutomationTrigger.TriggerType triggerType,
        String triggerRef
    ) {
        if (dryRunResult == null) {
            throw new IllegalArgumentException("dryRunResult must not be null");
        }

        String executionId = "dryrun-" + UUID.randomUUID().toString();
        String idempotencyKey = "dryrun-" + flowId + "-" + System.currentTimeMillis();

        // Map node results to node traces
        List<AutomationNodeExecutionTrace> nodeTraces = dryRunResult.nodeResults().stream()
            .map(AutomationDryRunTraceMapper::mapNodeResult)
            .collect(Collectors.toList());

        // Map validation issues
        List<com.example.platform.shared.capability.validation.AutomationFlowValidationIssue> issues =
            dryRunResult.validationResult() != null
                ? dryRunResult.validationResult().issues()
                : List.of();

        // Determine status
        AutomationExecutionTraceStatus status = mapStatus(dryRunResult.status(), nodeTraces);

        return new AutomationExecutionTrace(
            executionId,
            flowId,
            flowVersion,
            tenantId,
            triggerType,
            triggerRef,
            status,
            dryRunResult.startedAt(),
            dryRunResult.completedAt(),
            true, // dryRun
            null, // correlationId
            null, // causationId
            idempotencyKey,
            nodeTraces,
            issues,
            null, // logsRef
            java.util.Map.of() // metrics
        );
    }

    /**
     * Map a node dry-run result to a node execution trace.
     */
    private static AutomationNodeExecutionTrace mapNodeResult(AutomationNodeDryRunResult nodeResult) {
        AutomationNodeExecutionTraceStatus status = mapNodeStatus(nodeResult.status());

        return new AutomationNodeExecutionTrace(
            nodeResult.nodeId(),
            nodeResult.nodeType(),
            nodeResult.capabilityKey(),
            null, // capabilityVersion
            status,
            nodeResult.startedAt(),
            nodeResult.completedAt(),
            0, // attemptCount
            List.of(), // attempts
            nodeResult.output(),
            List.of(), // artifactRefs
            nodeResult.errorCode(),
            false, // retryable
            null // logsRef
        );
    }

    /**
     * Map dry-run status to execution trace status.
     */
    private static AutomationExecutionTraceStatus mapStatus(
        AutomationFlowDryRunStatus dryRunStatus,
        List<AutomationNodeExecutionTrace> nodeTraces
    ) {
        switch (dryRunStatus) {
            case SUCCEEDED:
                boolean hasNotImplemented = nodeTraces.stream()
                    .anyMatch(t -> t.status() == AutomationNodeExecutionTraceStatus.NOT_IMPLEMENTED);
                return hasNotImplemented
                    ? AutomationExecutionTraceStatus.DRY_RUN_PARTIALLY_SUPPORTED
                    : AutomationExecutionTraceStatus.DRY_RUN_SUCCEEDED;
            case VALIDATION_FAILED:
                return AutomationExecutionTraceStatus.VALIDATION_FAILED;
            case PARTIALLY_SUPPORTED:
                return AutomationExecutionTraceStatus.DRY_RUN_PARTIALLY_SUPPORTED;
            case FAILED:
                return AutomationExecutionTraceStatus.FAILED;
            default:
                return AutomationExecutionTraceStatus.FAILED;
        }
    }

    /**
     * Map node dry-run status to node execution trace status.
     */
    private static AutomationNodeExecutionTraceStatus mapNodeStatus(AutomationNodeDryRunStatus nodeStatus) {
        switch (nodeStatus) {
            case DRY_RUN_SUCCEEDED:
                return AutomationNodeExecutionTraceStatus.DRY_RUN_SUCCEEDED;
            case VALIDATION_FAILED:
                return AutomationNodeExecutionTraceStatus.VALIDATION_FAILED;
            case NOT_IMPLEMENTED:
                return AutomationNodeExecutionTraceStatus.NOT_IMPLEMENTED;
            case SKIPPED:
                return AutomationNodeExecutionTraceStatus.SKIPPED;
            case FAILED:
                return AutomationNodeExecutionTraceStatus.FAILED;
            default:
                return AutomationNodeExecutionTraceStatus.FAILED;
        }
    }
}
