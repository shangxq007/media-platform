package com.example.platform.workflow.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * User Workflow Execution V1 Temporal workflow (UWEV1-FV1).
 *
 * <p>Deterministic workflow code — ALL effects (PluginRuntime invocation,
 * persistence, outbox) live in {@link UserWorkflowExecutionActivities}
 * (WORKFLOW_CODE_IS_DETERMINISTIC; EFFECTS_LIVE_IN_ACTIVITIES).</p>
 */
@WorkflowInterface
public interface UserWorkflowExecutionWorkflow {

    @WorkflowMethod
    String run(String tenantId, String executionId, String definitionId, int definitionVersion,
               String trigger, String inputRefsJson);

    @SignalMethod
    void approve(String tenantId, String executionId, boolean approved, String approverActorId);

    @SignalMethod
    void cancel(String tenantId, String executionId, String reason);

    @QueryMethod
    String status(String tenantId, String executionId);
}
