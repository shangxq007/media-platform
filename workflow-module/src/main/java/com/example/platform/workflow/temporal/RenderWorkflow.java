package com.example.platform.workflow.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Durable render workflow (frozen contract TEPHV1 CONTRACT_V1, W1-GAP-006).
 *
 * <p>Conditional path 3 (activated, trigger proven): workflow-level
 * cancellation surface — {@code cancel} signal and {@code status} query — so
 * cancellation is distinguishable from failure/timeout/completed at the
 * workflow boundary. Payloads remain stable references (renderJobId,
 * tenantId): no raw timeline JSON, no RenderExecutionPlan payload, no media
 * bytes.</p>
 */
@WorkflowInterface
public interface RenderWorkflow {

    @WorkflowMethod
    String run(String renderJobId, String tenantId);

    /**
     * Cancellation signal (W1-GAP-006). Marks the workflow cancelled; the
     * implementation observes it between activity steps and reports
     * CANCELLED status.
     */
    @SignalMethod
    void cancel(String tenantId, String renderJobId, String reason);

    /**
     * Status query (W1-GAP-006/012). Returns a status string that
     * distinguishes RUNNING / COMPLETED / CANCELLED / FAILED.
     */
    @QueryMethod
    String status(String tenantId, String renderJobId);
}
