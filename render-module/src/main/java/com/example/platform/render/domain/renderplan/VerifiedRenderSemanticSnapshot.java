package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * ROADMAP20 correction R3-B1: ONE immutable integrity-bound authored semantic
 * snapshot consumed by the primary Render planning boundary.
 *
 * <p>Aggregates the verified Timeline revision projection and the verified
 * authored Effect semantic snapshot. The primary planner API consumes THIS type
 * (plus transient inputs) — it can no longer accept
 * {@code VerifiedTimelineRevision + arbitrary List<EffectInstance> +
 * arbitrary List<EffectDefinition>} as independently assemblable authored
 * inputs. ALL authored WHAT contributing to the Logical RenderPlan is bound
 * inside this snapshot:
 * <ul>
 *   <li>clips / audio mix / text elements — verified via
 *       {@link VerifiedTimelineRevisionFactory} (canonical content digest),</li>
 *   <li>effects / effect definitions — verified via
 *       {@link VerifiedEffectSemanticSnapshotFactory} (definition reference +
 *       version integrity, value-bound content pin).</li>
 * </ul>
 *
 * <p>Construction is package-restricted: the canonical constructor is
 * package-private; the intended production path is the
 * {@link VerifiedRenderSemanticSnapshotFactory}. Transient planning inputs
 * (RenderRequest, SourceResolutionInput, CapabilityContext) are NOT authored
 * revision truth and remain separate parameters of {@link RenderPlanningInput}.
 */
public record VerifiedRenderSemanticSnapshot(
        VerifiedTimelineRevision timelineRevision,
        VerifiedEffectSemanticSnapshot effectSemanticSnapshot) {

    public VerifiedRenderSemanticSnapshot {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(effectSemanticSnapshot, "effectSemanticSnapshot");
    }
}
