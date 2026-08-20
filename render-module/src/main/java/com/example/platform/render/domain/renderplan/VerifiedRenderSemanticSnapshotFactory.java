package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: factory producing the ONE immutable
 * integrity-bound authored semantic snapshot — the verified Render boundary
 * input (RENDER_CONSUMES_VERIFIED_EFFECT_SNAPSHOT_NOT_CALLER_EFFECT_LISTS_V1).
 *
 * <p>Both sub-verifications run their own fail-closed checks:
 * <ul>
 *   <li>Timeline: canonical content digest over the authoritative document
 *       (via {@link VerifiedTimelineRevisionFactory});</li>
 *   <li>Effects: the supplied {@link EffectSemanticSnapshot} is verified
 *       against the revision's EXACT pinned
 *       {@link EffectSemanticSnapshotReference} — snapshot id + content digest
 *       (stored AND recomputed) + contract version + structural integrity
 *       (via {@link VerifiedEffectSemanticSnapshotFactory}). Callers cannot
 *       pair a revision with a different snapshot, even one that is
 *       semantically identical (RP3-C/BI2).</li>
 * </ul>
 *
 * <p>The pure render planner consumes the resulting
 * {@link VerifiedRenderSemanticSnapshot}; it performs zero repository lookup.
 */
public final class VerifiedRenderSemanticSnapshotFactory {

    private VerifiedRenderSemanticSnapshotFactory() {
    }

    /**
     * Builds the verified authored semantic snapshot from the revision's
     * exact Effect pin.
     *
     * @param timelineRevision  authoritative Timeline revision (with canonicalTimeline)
     * @param digester          timeline canonical content digester
     * @param effectSnapshot    the immutable Effect semantic snapshot loaded by
     *                          the revision's pin
     * @param expectedReference the EXACT reference pinned by the revision
     * @return one immutable integrity-bound authored semantic snapshot
     * @throws IllegalArgumentException on any verification failure (fail closed)
     */
    public static VerifiedRenderSemanticSnapshot verified(
            TimelineRevision timelineRevision,
            TimelineContentDigester digester,
            EffectSemanticSnapshot effectSnapshot,
            EffectSemanticSnapshotReference expectedReference) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(digester, "digester");
        Objects.requireNonNull(effectSnapshot, "effectSnapshot");
        Objects.requireNonNull(expectedReference, "expectedReference");

        VerifiedTimelineRevision timeline = VerifiedTimelineRevisionFactory.verified(
                timelineRevision, digester);
        VerifiedEffectSemanticSnapshot effectsSnapshot =
                VerifiedEffectSemanticSnapshotFactory.verified(
                        effectSnapshot, expectedReference, timelineRevision.revisionId());
        return new VerifiedRenderSemanticSnapshot(timeline, effectsSnapshot);
    }
}
