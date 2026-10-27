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
 * Render remains the sole lifecycle owner: dispatch failure is projected through its public
 * lifecycle contract; a projection failure is preserved as a suppressed error. No local status
 * model, retry loop or compensation authority is introduced here.
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

        // Render's persisted preparation window covers save, dispatch and a crash before begin.
        var stage = com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.BOUND_GRAPH_SAVE;
        BoundGraphReference reference;
        try {
            lifecycle.preparationStage(tenantId, renderJobId, stage);
            reference = boundGraphInputStore.save(
                    planningAndBindingResult.boundGraphInputs(), tenantId, renderJobId);
        } catch (RuntimeException saveFailure) {
            try { lifecycle.failPreparation(tenantId, renderJobId, stage); }
            catch (RuntimeException projectionFailure) { saveFailure.addSuppressed(projectionFailure); }
            throw saveFailure;
        }
        lifecycle.preparationStage(tenantId, renderJobId,
                com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.DISPATCH);

        // 2. start only after the save returned the durable reference, used unmodified.
        boolean workflowStarted;
        try { workflowStarted = workflowStarter.start(reference, tenantId, workflowId); }
        catch(RuntimeException dispatchFailure) {
            try { lifecycle.failPreparation(tenantId,renderJobId,com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.DISPATCH); }
            catch(RuntimeException projectionFailure) { dispatchFailure.addSuppressed(projectionFailure); }
            throw dispatchFailure;
        }

        return new FirstRenderStartResult(workflowId, workflowStarted);
    }
}
