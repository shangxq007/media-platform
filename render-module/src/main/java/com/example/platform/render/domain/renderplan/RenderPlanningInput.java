package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Immutable planning input (C16, ROADMAP20 correction R3-B1): ONE
 * integrity-bound authored semantic snapshot + transient planning context.
 *
 * <p>ALL authored WHAT consumed by the Logical RenderPlan is bound inside
 * {@link VerifiedRenderSemanticSnapshot} (timeline revision projection
 * verified by canonical content digest; effect state verified by definition
 * reference/version integrity + value-bound content pin). The planner CANNOT
 * accept arbitrary caller-supplied {@code EffectInstance}/{@code EffectDefinition}
 * fragments alongside a verified revision — there is no such parameter.
 *
 * <p>Transient inputs remain separate and are NOT authored revision truth:
 * <ul>
 *   <li>{@link RenderRequest} — the planning request (id/extent/outputs),</li>
 *   <li>{@link SourceResolutionInput} — transient source availability,</li>
 *   <li>{@link CapabilityContext} — transient capability availability.</li>
 * </ul>
 * Resolution state and capability context are NOT fingerprint inputs; their
 * effect flows through node requirements/identity (C7).
 */
public record RenderPlanningInput(
        VerifiedRenderSemanticSnapshot authoredSnapshot,
        RenderRequest request,
        SourceResolutionInput resolution,
        CapabilityContext capabilities) {

    public RenderPlanningInput {
        Objects.requireNonNull(authoredSnapshot, "authoredSnapshot");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(capabilities, "capabilities");
    }

    /** Backwards-compatible accessor: the verified timeline revision projection. */
    public VerifiedTimelineRevision verifiedRevision() {
        return authoredSnapshot.timelineRevision();
    }

    /** Backwards-compatible accessor: the verified authored effect snapshot. */
    public VerifiedEffectSemanticSnapshot effectSemanticSnapshot() {
        return authoredSnapshot.effectSemanticSnapshot();
    }

    /** Backwards-compatible accessor: the pinned verified revision reference. */
    public TimelineRevisionReference revision() {
        return authoredSnapshot.timelineRevision().revision();
    }
}
