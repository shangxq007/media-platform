package com.example.platform.workflow.run;

import com.example.platform.outbox.api.event.OutboxDeliveryContext;
import com.example.platform.workflow.temporal.PlanWalkWorkflow;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.*;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Uses existing Outbox leases/retry/dead-letter recovery; no second dispatcher or live-request
 * dependency.
 */
@Component
public class WorkflowDispatch {
    private final WorkflowRunStore store;
    private final ObjectProvider<WorkflowClient> clients;

    public WorkflowDispatch(WorkflowRunStore store, ObjectProvider<WorkflowClient> clients) {
        this.store = store;
        this.clients = clients;
    }

    @EventListener
    public void start(RunEvents.Start event) {
        check(event.tenantId());
        var run = store.require(event.runId());
        if (!run.tenantId().equals(event.tenantId())
                || !run.workflowPlanDigest().equals(event.workflowPlanDigest()))
            throw new IllegalArgumentException("Start identity mismatch");
        ensureStarted(run);
    }

    @EventListener
    public void control(RunEvents.Control event) {
        check(event.tenantId());
        var run = store.require(event.runId());
        if (!run.tenantId().equals(event.tenantId()))
            throw new IllegalArgumentException("Control scope mismatch");
        if (WorkflowRunStore.terminal(run)) return;
        if (event.cancel()) {
            if (!run.cancelRequested())
                throw new IllegalArgumentException("Cancellation intent not accepted");
        } else {
            var waits = store.waitProjection(run.id(), event.stepId());
            if (waits.size() != 1
                    || !event.releaseId().equals(waits.getFirst().get("release_id"))
                    || !java.util.Objects.equals(event.approved(), waits.getFirst().get("approved"))
                    || waits.getFirst().get("released_by_json") == null)
                throw new IllegalArgumentException(
                        "Release intent does not match accepted wait command");
            if (!"WAITING".equals(waits.getFirst().get("status"))) return;
        }
        ensureStarted(run);
        var workflow = client().newWorkflowStub(PlanWalkWorkflow.class, workflowId(run.id()));
        try {
            if (event.cancel()) workflow.cancelRun();
            else workflow.release(event.stepId(), event.releaseId(), event.approved());
        } catch (WorkflowNotFoundException completedRace) {
            if (!WorkflowRunStore.terminal(store.require(run.id()))) throw completedRace;
        }
    }

    @SuppressWarnings("unchecked")
    public void ensureStarted(WorkflowRunStore.Run run) {
        if (WorkflowRunStore.terminal(run)) return;
        var workflow =
                client().newWorkflowStub(
                                PlanWalkWorkflow.class,
                                WorkflowOptions.newBuilder()
                                        .setWorkflowId(workflowId(run.id()))
                                        .setTaskQueue("workflow-process")
                                        .setMemo(
                                                Map.of(
                                                        "workflowRunId",
                                                        run.id(),
                                                        "planDigest",
                                                        run.workflowPlanDigest()))
                                        .setWorkflowIdReusePolicy(
                                                WorkflowIdReusePolicy
                                                        .WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                                        .build());
        try {
            WorkflowClient.start(
                    workflow::execute,
                    run.id(),
                    run.planJson(),
                    (Map<String, String>) RunJson.read(run.inputsJson(), Map.class),
                    null);
        } catch (WorkflowExecutionAlreadyStarted intendedExecutionExists) {
            var existing = client().newUntypedWorkflowStub(workflowId(run.id())).describe();
            if (!run.id().equals(existing.getMemo("workflowRunId", String.class))
                    || !run.workflowPlanDigest()
                            .equals(existing.getMemo("planDigest", String.class)))
                throw new IllegalStateException(
                        "Existing Temporal execution does not match accepted run pins");
        }
    }

    private WorkflowClient client() {
        var client = clients.getIfAvailable();
        if (client == null)
            throw new IllegalStateException(
                    "Temporal client unavailable; durable Outbox intent retained");
        return client;
    }

    private void check(String tenant) {
        if (!OutboxDeliveryContext.require().tenantId().equals(tenant))
            throw new IllegalArgumentException("Outbox tenant mismatch");
    }

    public static String workflowId(String runId) {
        return "workflow-run:" + runId;
    }
}
