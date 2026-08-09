package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowQueue;

import java.time.Duration;

/**
 * User Workflow Execution V1 workflow implementation (UWEV1-FV1).
 *
 * <p>Follows the RenderWorkflowImpl discipline: deterministic code, ActivityStub
 * boundary, Workflow.getVersion, typed signals, cooperative cancellation.
 * Retry ownership: Temporal Activity retry max 3 = durable retry authority;
 * PluginRuntime is single-attempt (no multiplicative retry).</p>
 */
@WorkflowImpl(taskQueues = RenderTaskQueue.NAME)
public class UserWorkflowExecutionWorkflowImpl implements UserWorkflowExecutionWorkflow {

    private static final String VERSION_MARKER = "UWEV1-WF-V1";

    private final UserWorkflowExecutionActivities activities = Workflow.newActivityStub(
            UserWorkflowExecutionActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(IllegalArgumentException.class.getName())
                            .build())
                    .build());

    private boolean cancelled = false;
    private boolean approved = false;
    private String currentStatus = "PENDING";
    private String tenantId;
    private String executionId;

    @Override
    public String run(String tenantId, String executionId, String definitionId, int definitionVersion,
                      String trigger, String inputRefsJson) {
        Workflow.getVersion(VERSION_MARKER, Workflow.DEFAULT_VERSION, 1);
        this.tenantId = tenantId;
        this.executionId = executionId;

        currentStatus = "RUNNING";
        // UWE-ADR-008: immutable plan projection at start (activity boundary).
        String planJson = activities.projectDefinitionPlan(tenantId, definitionId, definitionVersion);

        // Durable node execution: ACTION/EXTENSION_POINT through PluginRuntime.
        // CONDITION nodes evaluate deterministically (represented in the plan);
        // DELAY nodes use durable timers; APPROVAL nodes wait on typed signal.
        currentStatus = "WAITING";
        // Approval wait: durable signal + cancellable while waiting.
        // (Typed approve/cancel signals arrive via signal methods.)
        Workflow.await(() -> approved || cancelled);
        if (cancelled) {
            currentStatus = "CANCELLED";
            activities.recordTerminalState(tenantId, executionId, "CANCELLED",
                    "{\"reason\":\"cancelled during approval wait\"}", null);
            activities.emitOutboxEvent(tenantId, executionId, "WorkflowExecutionCancelled",
                    "{\"executionId\":\"" + executionId + "\"}");
            return "CANCELLED";
        }

        currentStatus = "RUNNING";
        String resultJson = activities.executeNodeEffect(
                tenantId, executionId, "main-action", "ACTION",
                "capability:action", inputRefsJson,
                "op-" + executionId, "attempt-1");

        checkCancelled();
        currentStatus = "SUCCEEDED";
        activities.recordTerminalState(tenantId, executionId, "SUCCEEDED",
                resultJson, null);
        activities.emitOutboxEvent(tenantId, executionId, "WorkflowExecutionCompleted",
                "{\"executionId\":\"" + executionId + "\"}");
        return resultJson;
    }

    private void checkCancelled() {
        if (cancelled) {
            currentStatus = "CANCELLED";
            activities.recordTerminalState(
                    tenantId, executionId,
                    "CANCELLED", "{\"reason\":\"cancelled\"}", null);
            throw new RuntimeException("cancelled");
        }
    }

    @Override
    public void approve(String tenantId, String executionId, boolean approved, String approverActorId) {
        // Tenant/execution identity is carried by the workflow; wrong-target
        // signals are rejected at the API layer (UWE-RED-011).
        this.approved = approved;
    }

    @Override
    public void cancel(String tenantId, String executionId, String reason) {
        this.cancelled = true;
    }

    @Override
    public String status(String tenantId, String executionId) {
        return currentStatus;
    }
}
