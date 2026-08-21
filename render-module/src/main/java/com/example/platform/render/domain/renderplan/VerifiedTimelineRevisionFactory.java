package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R2 B1: authoritative hydration boundary that produces a
 * {@link VerifiedTimelineRevision}.
 *
 * <p>This is the ONLY public construction path for the verified projection. It:
 * <ol>
 *   <li>loads the authoritative {@link TimelineRevision} (caller-supplied; this
 *       factory does NOT query repositories itself),</li>
 *   <li>computes the canonical content digest of the revision's
 *       {@code canonicalTimeline} using {@link TimelineContentDigester} (the
 *       timeline-module canonical digest authority),</li>
 *   <li>FAILS CLOSED on digest mismatch — a caller cannot bind revision identity
 *       R1 to fragments from R2/R3 because the projection is extracted from the
 *       SAME verified document, and the digest must match,</li>
 *   <li>extracts the typed semantic projection (clips, audio mix, text
 *       elements) from that same document,</li>
 *   <li>constructs the immutable {@link VerifiedTimelineRevision}.</li>
 * </ol>
 *
 * <p>Trust boundary: cryptographic revalidation of revision content against
 * persisted state happens here / at the application layer that supplies the
 * authoritative {@link TimelineRevision}. The pure render planner never queries
 * repositories and never loads mutable "latest" state.
 */
public final class VerifiedTimelineRevisionFactory {

    private VerifiedTimelineRevisionFactory() {
    }

    /**
     * Builds the verified planning projection from an authoritative
     * {@link TimelineRevision}. Fails closed on:
     * <ul>
     *   <li>missing canonicalTimeline (cannot verify),</li>
     *   <li>content digest mismatch (revision identity does not match content),</li>
     *   <li>unsupported source binding kind (fail closed, no silent default).</li>
     * </ul>
     *
     * @param timelineRevision authoritative immutable revision (with canonicalTimeline)
     * @param digester         timeline canonical content digester
     * @return verified immutable revision projection
     * @throws IllegalArgumentException if the revision cannot be verified
     */
    public static VerifiedTimelineRevision verified(
            TimelineRevision timelineRevision, TimelineContentDigester digester) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(digester, "digester");

        TimelineDocument document = timelineRevision.canonicalTimeline();
        if (document == null) {
            throw new IllegalArgumentException(
                    "Cannot verify TimelineRevision " + timelineRevision.revisionId()
                            + ": canonicalTimeline is absent (revision not hydrated)");
        }

        // 1. canonical content digest verification (fail closed on mismatch).
        // ROADMAP20 authority-integration: TimelineRevision.contentDigest is the
        // FULL revision semantic digest (timeline + Effect commitment) — the
        // TIMELINE-only digest lives in the revision semantic context and is
        // what the content digester recomputes.
        String computed = digester.digest(document);
        String recordedTimelineDigest = timelineRevision.semanticContext().timelineContentDigest();
        if (!computed.equals(recordedTimelineDigest)) {
            throw new IllegalArgumentException(
                    "TimelineRevision content digest mismatch for revision "
                            + timelineRevision.revisionId()
                            + ": computed=" + computed
                            + " recorded=" + recordedTimelineDigest);
        }

        // 2. extract typed semantic projection from the SAME verified document
        List<MediaClip> clips = extractClips(document);
        AudioMix audioMix = document.getAudioMix() != null ? document.getAudioMix() : AudioMix.EMPTY;
        List<TextElement> textElements = document.getTextElements() != null
                ? document.getTextElements() : List.of();

        // 3. construct the restricted immutable verified projection.
        // TimelineContentDigester returns a Base64 SHA-256; ContentDigest's
        // canonical form is lowercase hex — convert for the typed pin.
        TimelineRevisionReference reference = new TimelineRevisionReference(
                timelineRevision.revisionId(),
                ContentDigest.sha256(base64ToHex(recordedTimelineDigest)));
        return VerifiedTimelineRevision.create(reference, clips, audioMix, textElements);
    }

    /** Base64 SHA-256 -> lowercase hex (ContentDigest canonical form). */
    private static String base64ToHex(String base64Digest) {
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Digest);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Extracts typed {@link MediaClip}s from the canonical document. Timeline
     * clips are the canonical persistence projection; {@link MediaClip} is the
     * typed semantic authority. Source range semantics come from the frozen
     * trimStart/trimEnd (SOURCE-RANGE semantics, P2-B); temporal mapping is
     * consumed as authored. Unsupported source bindings fail closed (no silent
     * default).
     */
    private static List<MediaClip> extractClips(TimelineDocument document) {
        List<MediaClip> clips = new ArrayList<>();
        for (TimelineTrack track : document.getTracks()) {
            for (TimelineClip clip : track.clips()) {
                clips.add(toMediaClip(track, clip));
            }
        }
        return List.copyOf(clips);
    }

    private static MediaClip toMediaClip(TimelineTrack track, TimelineClip clip) {
        MediaTime timelineStart = clip.getStartTime();
        MediaTime timelineEnd = clip.getEndTime();
        MediaClip.TimeRange timelineRange = new MediaClip.TimeRange(timelineStart, timelineEnd);

        // P2-B: trimStart/trimEnd ARE source-range semantics; missing is
        // distinguishable from authored zero and must fail closed.
        MediaTime sourceStart = clip.getTrimStart();
        MediaTime sourceEnd = clip.getTrimEnd();
        if (sourceStart == null || sourceEnd == null) {
            throw new IllegalArgumentException(
                    "TimelineClip " + clip.getClipId().value()
                            + " requires exact source range (trimStart/trimEnd)");
        }
        MediaClip.TimeRange sourceRange = new MediaClip.TimeRange(sourceStart, sourceEnd);

        TimelineSourceBinding binding = timelineSourceBinding(clip, sourceRange);
        if (!(binding instanceof MediaStreamSourceBinding mediaBinding)) {
            throw new IllegalArgumentException(
                    "TimelineClip " + clip.getClipId().value()
                            + " has unsupported source binding kind: "
                            + (binding == null ? "null" : binding.getClass().getSimpleName()));
        }
        return new MediaClip(
                clip.getClipId().value(),
                track.trackId(),
                timelineRange,
                sourceRange,
                clip.getTemporalMapping(),
                mediaBinding);
    }

    private static TimelineSourceBinding timelineSourceBinding(
            TimelineClip clip, MediaClip.TimeRange sourceRange) {
        if (clip.getSourceKind() == null
                || !"MEDIA_STREAM".equalsIgnoreCase(clip.getSourceKind())) {
            throw new IllegalArgumentException(
                    "Unsupported source kind for clip " + clip.getClipId().value()
                            + ": " + clip.getSourceKind());
        }
        if (clip.getMediaAssetId() == null || clip.getMediaAssetId().isBlank()
                || clip.getMediaStreamId() == null || clip.getMediaStreamId().isBlank()
                || clip.getArtifactId() == null || clip.getArtifactId().isBlank()
                || clip.getContentDigest() == null || clip.getContentDigest().isBlank()) {
            throw new IllegalArgumentException(
                    "Partial source binding for clip " + clip.getClipId().value()
                            + ": MEDIA_STREAM requires mediaAssetId, mediaStreamId, "
                            + "artifactId and contentDigest");
        }
        return new MediaStreamSourceBinding(
                MediaAssetId.of(clip.getMediaAssetId()),
                MediaStreamId.of(clip.getMediaStreamId()),
                new ArtifactId(clip.getArtifactId()),
                ContentDigest.sha256(clip.getContentDigest()),
                sourceRange);
    }
}
