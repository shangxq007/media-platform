package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R5-A: THE authoritative issuer of
 * {@link EffectSemanticBinding}.
 *
 * <p>This is the ONLY public path that can produce a valid binding. It:
 * <ol>
 *   <li>extracts the revision identity FROM the authoritative
 *       {@link TimelineRevision} object (never accepts a caller-supplied
 *       revision label),</li>
 *   <li>performs a REAL ownership check: every effect's application range must
 *       overlap at least one clip in the revision's canonical timeline —
 *       otherwise the effect state does not belong to that revision and
 *       issuance FAILS CLOSED (a caller cannot attach an unrelated-but-valid
 *       effect set to a revision whose clips it does not apply to),</li>
 *   <li>delegates the content digest to the single Effect domain canonical
 *       authority ({@link EffectSemanticStateCanonicalSemantics}),</li>
 *   <li>returns an immutable {@link EffectSemanticBinding}.</li>
 * </ol>
 *
 * <p>There is no public {@code of(revisionId, effects, defs)} escape hatch:
 * a planning caller cannot relabel effect state R2 as revision R1.
 */
public final class AuthoredEffectSemanticAuthority {

    private AuthoredEffectSemanticAuthority() {
    }

    /**
     * Issues the authoritative binding for the given authored revision and its
     * Effect semantic state.
     *
     * @param timelineRevision  the authoritative Timeline revision (revision id
     *                          and canonical clips are read from THIS object)
     * @param effects           typed effect instances claimed to belong to the revision
     * @param effectDefinitions typed effect definition catalog
     * @return authoritative immutable binding
     * @throws IllegalArgumentException if any effect's application range does
     *                                  not overlap the revision's clips (fail
     *                                  closed — ownership not established), or
     *                                  definition references are invalid
     */
    public static EffectSemanticBinding issue(
            TimelineRevision timelineRevision,
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");

        // 1. revision identity comes from the authoritative object.
        String revisionId = timelineRevision.revisionId();

        // 2. ownership check: every effect must apply to the revision's clips.
        List<MediaClip.TimeRange> clipRanges = clipRangesOf(timelineRevision.canonicalTimeline());
        for (EffectInstance effect : effects) {
            boolean overlapsAnyClip = clipRanges.stream()
                    .anyMatch(range -> range.overlaps(effect.applicationRange()));
            if (!overlapsAnyClip) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " application range "
                                + effect.applicationRange() + " does not overlap any clip in "
                                + "revision " + revisionId + " — effect state does not belong "
                                + "to this authored revision (R5-A ownership fail-closed)");
            }
        }

        // 3. digest via the single domain authority.
        String canonical = EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                effects, effectDefinitions);
        ContentDigest digest = ContentDigest.sha256(
                EffectSemanticStateCanonicalSemantics.sha256Hex(canonical));

        // 4. immutable binding (package-private construction).
        return EffectSemanticBinding.create(revisionId, digest, EffectSemanticBinding.CONTRACT_VERSION);
    }

    /** All clip timeline ranges in the revision's canonical document. */
    private static List<MediaClip.TimeRange> clipRangesOf(TimelineDocument document) {
        if (document == null) {
            return List.of();
        }
        java.util.ArrayList<MediaClip.TimeRange> ranges = new java.util.ArrayList<>();
        for (TimelineTrack track : document.getTracks()) {
            for (TimelineClip clip : track.clips()) {
                ranges.add(new MediaClip.TimeRange(clip.getStartTime(), clip.getEndTime()));
            }
        }
        return ranges;
    }
}
