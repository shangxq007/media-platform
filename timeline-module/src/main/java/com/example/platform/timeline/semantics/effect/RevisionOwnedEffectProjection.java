package com.example.platform.timeline.semantics.effect;

import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ROADMAP20 correction R6-A: the revision-owned authored Effect membership
 * projection.
 *
 * <p>Repository reality (R5 publication): the authoritative wire Effect state
 * lives in {@code TimelineCandidate.Clip.effects} — the revision-owned
 * aggregate parsed from the revision's wire JSON. This projection flattens
 * that aggregate into explicit typed membership entries:
 *
 * <pre>
 * (trackId, clipId, effectWireId, effectKey)
 * </pre>
 *
 * <p>Each entry is the authentic authored relation "effect e belongs to clip
 * c on track t". It is derived ONLY from the revision-owned candidate — never
 * from a caller's label — and preserves the authored effect stack order
 * (wire list order; see {@code EFFECT_STACK_ORDER_SEMANTICS = ORDERED}).
 *
 * <p>This is the SINGLE membership authority the
 * {@link AuthoredEffectSemanticAuthority} verifies against; there is no
 * temporal-overlap heuristic anywhere in the membership path.
 */
public final class RevisionOwnedEffectProjection {

    /**
     * One authored membership entry: an effect (wire id + definition key)
     * belonging to a specific clip on a specific track.
     */
    public record Member(String trackId, String clipId, String effectWireId, String effectKey) {
        public Member {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(clipId, "clipId");
            Objects.requireNonNull(effectWireId, "effectWireId");
            Objects.requireNonNull(effectKey, "effectKey");
        }
    }

    private final List<Member> members;

    private RevisionOwnedEffectProjection(List<Member> members) {
        this.members = List.copyOf(members);
    }

    /** Builds the projection from the revision-owned candidate aggregate. */
    public static RevisionOwnedEffectProjection fromCandidate(TimelineCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        List<Member> members = new ArrayList<>();
        for (TimelineCandidate.Track track : candidate.tracks()) {
            for (TimelineCandidate.Clip clip : track.clips()) {
                for (TimelineClipEffect effect : clip.effects()) {
                    members.add(new Member(track.trackId(), clip.clipId(),
                            effect.id() != null ? effect.id() : "", effect.effectKey()));
                }
            }
        }
        return new RevisionOwnedEffectProjection(members);
    }

    /** All membership entries (authored effect stack order preserved). */
    public List<Member> members() {
        return members;
    }

    /**
     * True iff the projection contains an authored membership for the given
     * effect instance on the given target clip.
     */
    public boolean contains(ClipEffectTarget target, String effectInstanceId) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effectInstanceId, "effectInstanceId");
        return members.stream().anyMatch(m ->
                m.trackId().equals(target.trackId())
                        && m.clipId().equals(target.clipId())
                        && m.effectWireId().equals(effectInstanceId));
    }

    /**
     * The authored wire membership entry for the effect instance on the target,
     * or empty if the membership does not exist (fail-closed boundary).
     */
    public Optional<Member> memberFor(ClipEffectTarget target, String effectInstanceId) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effectInstanceId, "effectInstanceId");
        return members.stream()
                .filter(m -> m.trackId().equals(target.trackId())
                        && m.clipId().equals(target.clipId())
                        && m.effectWireId().equals(effectInstanceId))
                .findFirst();
    }

    /** True iff the projection has no authored effects at all. */
    public boolean isEmpty() {
        return members.isEmpty();
    }
}
