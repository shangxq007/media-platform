package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Immutable planning input (C16, ROADMAP20 correction F4): one coherent
 * {@link HydratedTimelineRevision} + RenderRequest + transient planning context.
 *
 * <p>Callers CANNOT independently supply a revision reference plus arbitrarily
 * assembled authored fragments through this primary API — the authored semantic
 * projection (clips, effects, definitions, audio mix, text elements) is
 * integrity-bound inside {@code hydratedRevision}. Resolution state and
 * capability context are transient (NOT fingerprint inputs; their effect flows
 * through node requirements/identity, C7).
 */
public record RenderPlanningInput(
        HydratedTimelineRevision hydratedRevision,
        RenderRequest request,
        SourceResolutionInput resolution,
        CapabilityContext capabilities) {

    public RenderPlanningInput {
        Objects.requireNonNull(hydratedRevision, "hydratedRevision");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(capabilities, "capabilities");
    }

    /** Backwards-compatible accessor: the pinned revision reference. */
    public TimelineRevisionReference revision() {
        return hydratedRevision.revision();
    }
}
