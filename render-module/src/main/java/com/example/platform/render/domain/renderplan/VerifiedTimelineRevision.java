package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.clip.MediaClip;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R2 B1: VERIFIED immutable revision projection accepted
 * by the primary Render planning boundary.
 *
 * <p>Unlike a casually-assembled hydrated record, this type represents ONE
 * coherent immutable Timeline revision projection whose revision identity +
 * content digest have been validated against the canonical hydrated content at
 * an application/hydration boundary (see {@code VerifiedTimelineRevisionFactory}).
 *
 * <p>Construction is RESTRICTED: the constructor is private, and the ONLY
 * public construction path is
 * {@code VerifiedTimelineRevisionFactory.verified(TimelineRevision, TimelineContentDigester)}
 * which (a) computes the canonical content digest of the authoritative
 * TimelineDocument, (b) fails closed on mismatch, and (c) extracts the typed
 * semantic projection from that same document. Arbitrary external code CANNOT
 * construct a value claiming to be verified from unrelated revision fragments.
 *
 * <p>The pure render planner consumes this object; it never queries
 * repositories, never loads mutable "latest" state, and never performs
 * infrastructure hydration. Effects and effect definitions are NOT part of the
 * verified revision projection because the authoritative
 * {@code TimelineDocument} does not carry effects (repository reality); they
 * are supplied as separate explicit planning inputs.
 */
public final class VerifiedTimelineRevision {

    private final TimelineRevisionReference revision;
    private final List<MediaClip> clips;
    private final AudioMix audioMix;
    private final List<TextElement> textElements;

    /** Restricted constructor — private on purpose; factory-only construction. */
    private VerifiedTimelineRevision(
            TimelineRevisionReference revision,
            List<MediaClip> clips,
            AudioMix audioMix,
            List<TextElement> textElements) {
        this.revision = Objects.requireNonNull(revision, "revision");
        this.clips = List.copyOf(clips);
        this.audioMix = Objects.requireNonNull(audioMix, "audioMix");
        this.textElements = List.copyOf(textElements);
    }

    /** Factory-only construction path (see {@link VerifiedTimelineRevisionFactory}). */
    static VerifiedTimelineRevision create(
            TimelineRevisionReference revision,
            List<MediaClip> clips,
            AudioMix audioMix,
            List<TextElement> textElements) {
        return new VerifiedTimelineRevision(revision, clips, audioMix, textElements);
    }

    public TimelineRevisionReference revision() {
        return revision;
    }

    public List<MediaClip> clips() {
        return clips;
    }

    public AudioMix audioMix() {
        return audioMix;
    }

    public List<TextElement> textElements() {
        return textElements;
    }

    /** Content digest pin of the verified immutable revision (provenance/fingerprint). */
    public ContentDigest contentDigest() {
        return revision.contentDigest();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VerifiedTimelineRevision v)) {
            return false;
        }
        return revision.equals(v.revision) && clips.equals(v.clips)
                && audioMix.equals(v.audioMix) && textElements.equals(v.textElements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revision, clips, audioMix, textElements);
    }

    @Override
    public String toString() {
        return "VerifiedTimelineRevision(revision=" + revision
                + ", clips=" + clips.size() + ", textElements=" + textElements.size() + ")";
    }
}
