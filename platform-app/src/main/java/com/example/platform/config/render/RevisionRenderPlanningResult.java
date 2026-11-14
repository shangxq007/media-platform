package com.example.platform.config.render;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import java.util.List;
import java.util.Objects;

/**
 * Internal result of one revision-pinned render planning preparation.
 *
 * <p>Carries only constructed inputs and context — no planning (#20), no execution planning (#21),
 * no provider binding (#22), no bound-graph persistence and no workflow start.
 *
 * @param renderJobId        the durable render job identity created via the render-job boundary
 * @param renderRequest      the transient render request (independent correlation id)
 * @param executionPlanId    the independent execution plan identity
 * @param sourceResolution   per-artifact source resolution state
 * @param capabilityContext  the available capability set for this planning context
 * @param declarations       the declared provider-boundary compatibility declarations
 */
public record RevisionRenderPlanningResult(
        String renderJobId,
        RenderRequest renderRequest,
        ExecutionPlanId executionPlanId,
        SourceResolutionInput sourceResolution,
        CapabilityContext capabilityContext,
        List<ProviderBoundaryCompatibilityDeclaration> declarations) {

    public RevisionRenderPlanningResult {
        Objects.requireNonNull(renderJobId, "renderJobId");
        Objects.requireNonNull(renderRequest, "renderRequest");
        Objects.requireNonNull(executionPlanId, "executionPlanId");
        Objects.requireNonNull(sourceResolution, "sourceResolution");
        Objects.requireNonNull(capabilityContext, "capabilityContext");
        Objects.requireNonNull(declarations, "declarations");
        declarations = List.copyOf(declarations);
    }
}
