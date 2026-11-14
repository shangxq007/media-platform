package com.example.platform.config.render;

import com.example.platform.execution.binding.BoundGraphReference;

/**
 * Phase 6 seam: starts the {@code MediaTaskWorkflow} for a saved bound-graph reference.
 *
 * <p>Separation keeps the temporal startup mechanism out of the save/start orchestration logic so
 * that the orchestration (ordering, reference passthrough, failure propagation) can be verified
 * without a Temporal cluster.
 */
public interface RenderJobWorkflowStarter {

    /**
     * Starts the workflow addressed by {@code workflowId} with the given reference.
     *
     * @return {@code true} when this call started the workflow OR the target workflow already
     *     exists ({@code WorkflowExecutionAlreadyStarted}); any other failure is thrown
     */
    boolean start(BoundGraphReference reference, String tenantId, String workflowId);
}
