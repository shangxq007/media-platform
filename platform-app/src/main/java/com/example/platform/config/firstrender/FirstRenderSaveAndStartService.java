package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.*;
import com.example.platform.render.api.GraphRenderLifecycle;
import org.springframework.stereotype.Service;

/** Saves immutable execution inputs inside the atomic First Render submission. No external dispatch here. */
@Service
public class FirstRenderSaveAndStartService {
    private final BoundGraphInputStore store;
    private final GraphRenderLifecycle lifecycle;
    public FirstRenderSaveAndStartService(BoundGraphInputStore store, GraphRenderLifecycle lifecycle) {
        this.store = store; this.lifecycle = lifecycle;
    }
    public BoundGraphReference save(FirstRenderPlanningAndBindingResult plan) {
        lifecycle.preparationStage(plan.tenantId(), plan.renderJobId(), GraphRenderLifecycle.PreparationStage.BOUND_GRAPH_SAVE);
        BoundGraphReference reference;
        try { reference = store.save(plan.boundGraphInputs(), plan.tenantId(), plan.renderJobId()); }
        catch (RuntimeException failure) {
            try { lifecycle.failPreparation(plan.tenantId(), plan.renderJobId(), GraphRenderLifecycle.PreparationStage.BOUND_GRAPH_SAVE); }
            catch (RuntimeException projection) { failure.addSuppressed(projection); }
            throw failure;
        }
        lifecycle.preparationStage(plan.tenantId(), plan.renderJobId(), GraphRenderLifecycle.PreparationStage.DISPATCH);
        return reference;
    }
}
