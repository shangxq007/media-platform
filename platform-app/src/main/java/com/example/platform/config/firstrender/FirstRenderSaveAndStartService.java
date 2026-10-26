package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphReference;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Phase 6 orchestration: persist the Phase 5 bound-graph inputs, then start the media task workflow.
 *
 * <p>Order is fixed and fail-closed: the bound-graph inputs are saved first and the workflow is
 * started only after a successful save with the exact {@link BoundGraphReference} returned by the
 * store. A save failure propagates before any start; a first start failure propagates to the caller.
 * The service does not advance {@code render_job.status}, does not retry, and does not compensate.
 */
@Service
public class FirstRenderSaveAndStartService {

    private final BoundGraphInputStore boundGraphInputStore;
    private final FirstRenderWorkflowStarter workflowStarter;
    private final com.example.platform.render.api.GraphRenderLifecycle lifecycle;

    public FirstRenderSaveAndStartService(
            BoundGraphInputStore boundGraphInputStore,
            FirstRenderWorkflowStarter workflowStarter, com.example.platform.render.api.GraphRenderLifecycle lifecycle) {
        this.boundGraphInputStore = Objects.requireNonNull(boundGraphInputStore, "boundGraphInputStore");
        this.workflowStarter = Objects.requireNonNull(workflowStarter, "workflowStarter");
        this.lifecycle=Objects.requireNonNull(lifecycle);
    }

    /**
     * Saves the Phase 5 bound-graph inputs and starts the workflow for the saved reference.
     *
     * @throws RuntimeException when the save fails (the workflow is never started), or when the
     *     first start fails
     */
    public FirstRenderStartResult start(FirstRenderPlanningAndBindingResult planningAndBindingResult) {
        Objects.requireNonNull(planningAndBindingResult, "planningAndBindingResult");
        String tenantId = planningAndBindingResult.tenantId();
        String renderJobId = planningAndBindingResult.renderJobId();
        // Frozen A6 workflow identity.
        String workflowId = com.example.platform.workflow.temporal.RenderWorkflowIdentity.forJob(tenantId,renderJobId);

        // 1. save first — a failure here propagates before any start.
        BoundGraphReference reference = boundGraphInputStore.save(
                planningAndBindingResult.boundGraphInputs(), tenantId, renderJobId);

        // 2. start only after the save returned the durable reference, used unmodified.
        boolean workflowStarted;
        try { workflowStarted = workflowStarter.start(reference, tenantId, workflowId); }
        catch(RuntimeException dispatchFailure) {
            try { lifecycle.fail(tenantId,renderJobId,reference.planRef(),reference.planDigest(),reference.expectedExecutableTaskGraphDigest(),false); }
            catch(RuntimeException projectionFailure) { dispatchFailure.addSuppressed(projectionFailure); }
            throw dispatchFailure;
        }

        return new FirstRenderStartResult(workflowId, workflowStarted);
    }
}
