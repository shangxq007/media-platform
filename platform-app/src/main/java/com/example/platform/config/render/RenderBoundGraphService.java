package com.example.platform.config.render;

import com.example.platform.execution.binding.*;
import com.example.platform.render.api.GraphRenderLifecycle;
import org.springframework.stereotype.Service;

/** Saves immutable execution inputs inside the atomic First Render submission. No external dispatch here. */
@Service
public class RenderBoundGraphService {
    private final BoundGraphInputStore store;
    private final GraphRenderLifecycle lifecycle;
    public RenderBoundGraphService(BoundGraphInputStore store, GraphRenderLifecycle lifecycle) {
        this.store = store; this.lifecycle = lifecycle;
    }
    public BoundGraphReference save(RevisionRenderPlanningAndBindingResult plan) {
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
