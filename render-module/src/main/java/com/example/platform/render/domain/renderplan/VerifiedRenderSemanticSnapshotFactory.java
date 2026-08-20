package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R3-B1: factory producing the ONE immutable
 * integrity-bound authored semantic snapshot.
 *
 * <p>This is the production path that binds the verified Timeline revision
 * projection and the verified authored Effect semantic snapshot into a single
 * planning snapshot. Both sub-verifications run their own fail-closed checks
 * (canonical content digest for the timeline; definition reference/version
 * integrity + value-bound content pin for effects).
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
     * @return one immutable integrity-bound authored semantic snapshot
     * @throws IllegalArgumentException on any verification failure (fail closed)
     */
    public static VerifiedRenderSemanticSnapshot verified(
            TimelineRevision timelineRevision,
            TimelineContentDigester digester,
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(digester, "digester");
        VerifiedTimelineRevision timeline = VerifiedTimelineRevisionFactory.verified(
                timelineRevision, digester);
        VerifiedEffectSemanticSnapshot effectsSnapshot =
                VerifiedEffectSemanticSnapshotFactory.verified(effects, effectDefinitions);
        return new VerifiedRenderSemanticSnapshot(timeline, effectsSnapshot);
    }
}
