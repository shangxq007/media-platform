package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * User Workflow Execution effect activities (UWEV1-FV1).
 *
 * <p>All external effects execute here: definition projection, node execution
 * via PluginRuntime (extension::runtime), terminal persistence, outbox.</p>
 */
@ActivityInterface
public interface UserWorkflowExecutionActivities {

    /** Project the PUBLISHED definition version into an immutable execution plan (UWE-ADR-008). */
    @ActivityMethod
    String projectDefinitionPlan(String tenantId, String definitionId, int definitionVersion);

    /** Execute a single node's effect through PluginRuntime (ACTION / EXTENSION_POINT). */
    @ActivityMethod
    String executeNodeEffect(String tenantId, String executionId, String nodeId, String nodeType,
                             String capabilityRef, String inputJson, String operationId, String attemptId);

    /** Persist the terminal product-authority projection (success/failure/cancel/timeout). */
    @ActivityMethod
    void recordTerminalState(String tenantId, String executionId, String status,
                             String resultSummaryJson, String errorCategory);

    /** Emit durable terminal outbox event. */
    @ActivityMethod
    void emitOutboxEvent(String tenantId, String executionId, String eventType, String payloadJson);
}
