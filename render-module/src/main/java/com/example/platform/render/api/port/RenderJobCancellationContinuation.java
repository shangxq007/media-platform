package com.example.platform.render.api.port;

/**
 * Continues render cancellation after a {@code render_job} status change to
 * CANCELLED (frozen contract TEPHV1 CONTRACT_V1, W1-GAP-006).
 *
 * <p>Render-side port (defined here so render-module never depends on
 * workflow-module); implementations live in workflow-module:
 * TemporalRenderCancellationContinuation (WorkflowClient.cancel when
 * mode=temporal) and LocalRenderCancellationContinuation (no-op, local mode).
 * Invoked exactly once from RenderJobService.cancel after the render_job
 * status update. The workflow is cancelled through its durable execution —
 * cancellation does NOT mutate Timeline, Product READY or RenderExecutionPlan
 * state.</p>
 */
public interface RenderJobCancellationContinuation {

    /**
     * Propagate an application-level cancellation of a render job to the
     * underlying execution mechanism.
     *
     * @param tenantId tenant identifier
     * @param jobId    render job id (== Temporal workflow id in temporal mode)
     */
    void cancelAfterJobCancelled(String tenantId, String jobId);
}
