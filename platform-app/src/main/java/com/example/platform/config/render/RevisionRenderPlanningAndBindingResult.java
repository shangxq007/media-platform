package com.example.platform.config.render;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import java.util.Objects;

/**
 * Result of the revision-pinned render planning-and-binding coordination.
 *
 * <p>Carries the complete {@link BoundGraphInputs} (physical plan + declared candidates + transition
 * declarations + the {@code expectedExecutableTaskGraphDigest} produced by the real #22 binding) plus
 * the tenant and render-job scope. Nothing is persisted and no workflow is started.
 *
 * @param tenantId        owning tenant
 * @param renderJobId     the render job identity created during planning preparation
 * @param renderRequestId the request correlation id produced by planning preparation (D2)
 * @param boundGraphInputs the fully constructed bound-graph inputs (never saved here)
 */
public record RevisionRenderPlanningAndBindingResult(
        String tenantId,
        String renderJobId,
        RenderRequestId renderRequestId,
        BoundGraphInputs boundGraphInputs) {

    public RevisionRenderPlanningAndBindingResult {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(renderRequestId, "renderRequestId");
        Objects.requireNonNull(boundGraphInputs, "boundGraphInputs");
    }
}
