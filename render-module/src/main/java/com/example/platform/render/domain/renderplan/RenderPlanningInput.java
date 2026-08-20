package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.util.List;
import java.util.Objects;

/**
 * Immutable planning input (C16, ROADMAP20 correction R2 B1): one coherent
 * VERIFIED {@link VerifiedTimelineRevision} + RenderRequest + transient planning
 * context.
 *
 * <p>Callers CANNOT mix a revision reference with arbitrarily assembled authored
 * fragments: the clip/audio/text projection is integrity-bound inside
 * {@code verifiedRevision} (construction restricted to
 * {@link VerifiedTimelineRevisionFactory}, which validates the canonical content
 * digest). Effects and effect definitions are separate explicit planning inputs
 * because the authoritative TimelineDocument does not carry effects (repository
 * reality — the pure planner never loads them itself).
 *
 * <p>Resolution state and capability context are transient (NOT fingerprint
 * inputs; their effect flows through node requirements/identity, C7).
 */
public record RenderPlanningInput(
        VerifiedTimelineRevision verifiedRevision,
        List<EffectInstance> effects,
        List<EffectInstance.EffectDefinition> effectDefinitions,
        RenderRequest request,
        SourceResolutionInput resolution,
        CapabilityContext capabilities) {

    public RenderPlanningInput {
        Objects.requireNonNull(verifiedRevision, "verifiedRevision");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(capabilities, "capabilities");
        effects = List.copyOf(effects);
        effectDefinitions = List.copyOf(effectDefinitions);
    }

    /** Backwards-compatible accessor: the pinned verified revision reference. */
    public TimelineRevisionReference revision() {
        return verifiedRevision.revision();
    }
}
