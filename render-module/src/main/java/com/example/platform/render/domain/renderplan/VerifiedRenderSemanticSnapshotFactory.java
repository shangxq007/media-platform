package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R4-A1: factory producing the ONE immutable
 * integrity-bound authored semantic snapshot.
 *
 * <p>Both sub-verifications run their own fail-closed checks:
 * <ul>
 *   <li>Timeline: canonical content digest over the authoritative document
 *       (via {@link VerifiedTimelineRevisionFactory});</li>
 *   <li>Effects: authoritative {@link EffectSemanticBinding} recomputed by the
 *       single Timeline/Effect domain authority must match the supplied binding
 *       digest, AND {@code binding.revisionId()} must equal the planned
 *       {@code timelineRevision.revisionId()} — otherwise FAIL CLOSED
 *       (cross-revision/context effect assembly is impossible).</li>
 * </ul>
 *
 * <p>The pure render planner consumes the resulting
 * {@link VerifiedRenderSemanticSnapshot}; it performs zero repository lookup.
 */
public final class VerifiedRenderSemanticSnapshotFactory {

    private VerifiedRenderSemanticSnapshotFactory() {
    }

    /**
     * Builds the verified authored semantic snapshot.
     *
     * @param timelineRevision   authoritative Timeline revision (with canonicalTimeline)
     * @param digester           timeline canonical content digester
     * @param effects            typed authored effect instances
     * @param effectDefinitions  typed effect definition catalog
     * @param effectBinding      authoritative Effect semantic binding (revision
     *                           id + digest, computed by the Effect domain)
     * @return one immutable integrity-bound authored semantic snapshot
     * @throws IllegalArgumentException on any verification failure (fail closed)
     */
    public static VerifiedRenderSemanticSnapshot verified(
            TimelineRevision timelineRevision,
            TimelineContentDigester digester,
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            EffectSemanticBinding effectBinding) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(digester, "digester");
        Objects.requireNonNull(effectBinding, "effectBinding");

        // R4-A1: the authored effect binding MUST be for the SAME timeline
        // revision being planned (fail closed on cross-revision assembly).
        if (!effectBinding.revisionId().equals(timelineRevision.revisionId())) {
            throw new IllegalArgumentException(
                    "Effect semantic binding revision mismatch (R4-A1): effect state "
                            + "bound to revision '" + effectBinding.revisionId()
                            + "' cannot be combined with timeline revision '"
                            + timelineRevision.revisionId() + "'");
        }

        VerifiedTimelineRevision timeline = VerifiedTimelineRevisionFactory.verified(
                timelineRevision, digester);
        VerifiedEffectSemanticSnapshot effectsSnapshot =
                VerifiedEffectSemanticSnapshotFactory.verified(
                        effects, effectDefinitions, effectBinding);
        return new VerifiedRenderSemanticSnapshot(timeline, effectsSnapshot);
    }
}
