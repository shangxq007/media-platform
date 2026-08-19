package com.example.platform.timeline.app;

import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineModelPath;
import com.example.platform.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Frozen internal adapter (NDSF-SCOPE-E1 F005-F010): {@link TimelineDocument} ->
 * {@link TimelineCandidate}. Pure and deterministic; no repository, BlobStorage,
 * ProductRuntimeService, network, current-time, or randomness access.
 * <p>
 * Implements the frozen field-mapping TSV exactly:
 * timelineId := productId (frozen derivation); profile = CANONICAL_TIMELINE_FOUNDATION_V1;
 * zOrder default 0; audioGain default null; Duration -> MediaTime.ofRational(nanos, 1e9);
 * sourceRef from assetId. Structural failures raise
 * {@link TimelineCanonicalRejectionException} with the five frozen adapter codes.
 */
public final class TimelineDocumentCandidateMapper {

    private TimelineDocumentCandidateMapper() {
    }

    static TimelineCandidate map(String productId, TimelineDocument document) {
        if (!TimelineDocument.CURRENT_SCHEMA_VERSION.equals(document.getSchemaVersion())) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                    TimelineModelPath.root().field("schemaVersion"),
                    "Unsupported TimelineDocument schema version: " + document.getSchemaVersion()));
        }
        List<TimelineCandidate.Track> tracks = new ArrayList<>();
        for (TimelineTrack track : document.getTracks()) {
            tracks.add(mapTrack(track));
        }
        // F009: timelineId derived deterministically from productId (the Timeline belongs
        // to the product; TimelineDocument carries no timeline identifier).
        // CHECKPOINT_A correction: full authored semantic surface — no silent
        // narrowing of textElements/audioMix/relationships (TimelineDocument
        // carries no transitions/automations fields; those live in internal
        // payloads and remain List.of() here).
        return new TimelineCandidate(productId, productId,
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, tracks,
                List.of(), List.of(),
                document.getTextElements() != null ? List.copyOf(document.getTextElements()) : List.of(),
                document.getAudioMix() != null ? document.getAudioMix() : com.example.platform.audio.domain.mix.AudioMix.empty(),
                document.getSemanticRelationships() != null ? List.copyOf(document.getSemanticRelationships()) : List.of());
    }

    private static TimelineCandidate.Track mapTrack(TimelineTrack track) {
        List<TimelineCandidate.Clip> clips = new ArrayList<>();
        for (TimelineClip clip : track.clips()) {
            clips.add(mapClip(clip));
        }
        // F007: zOrder default 0, audioGain default null (TimelineTrack has no such fields).
        return new TimelineCandidate.Track(track.trackId(), mapType(track.type(), track.trackId()), 0, null, clips);
    }

    private static TimelineCandidate.TrackType mapType(TrackType type, String trackId) {
        if (type == null) {
            return TimelineCandidate.TrackType.VIDEO; // TimelineTrack defaults null to VIDEO
        }
        return switch (type) {
            case VIDEO -> TimelineCandidate.TrackType.VIDEO;
            case AUDIO -> TimelineCandidate.TrackType.AUDIO;
            default -> throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                            TimelineModelPath.root().field("tracks").id(trackId == null || trackId.isBlank() ? "unknown" : trackId).field("type"),
                            "Unsupported track type: " + type));
        };
    }

    private static TimelineCandidate.Clip mapClip(TimelineClip clip) {
        String clipId = clip.getClipId().value();
        if (clipId == null || clipId.isBlank() || !clipId.equals(clipId.strip())) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").field("clipId"),
                    "Clip identifier must be nonblank and already normalized"));
        }
        String mediaAssetId = clip.getMediaAssetId();
        if (mediaAssetId == null || mediaAssetId.isBlank() || !mediaAssetId.equals(mediaAssetId.strip())) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("sourceRef"),
                    "Source reference (mediaAssetId) must be nonblank and already normalized"));
        }
        MediaTime timelineStart = clip.getStartTime();
        MediaTime sourceStart = clip.getTrimStart();
        MediaTime duration;
        try {
            if (clip.getEndTime().isLessThan(clip.getStartTime())) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                                TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("duration"),
                                "Clip endTime must not precede startTime"));
            }
            duration = clip.getEndTime().subtract(clip.getStartTime());
        } catch (ArithmeticException overflow) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("duration"),
                    "Clip duration overflow"));
        }
        // CHECKPOINT_A correction: carry the FULL typed source semantics of the
        // canonical clip (kind/asset/stream/artifact/digest/temporal mapping) —
        // never narrow to assetId alone.
        // R4-B: the typed TimelineSourceBinding is the merge-path authority;
        // built from the document clip's flat fields via the binding authority.
        // R5-B: the document clip's flat fields are canonicalized immediately
        // into ONE typed binding; partial/invalid source-binding intent FAILS
        // CLOSED (no catch→null narrowing).
        return new TimelineCandidate.Clip(clipId, TimelineSourceRef.of(mediaAssetId),
                timelineStart, sourceStart, duration, FrameRate.of(30, 1), List.of(), List.of(),
                clip.getTemporalMapping(), typedBindingOf(clip, clipId));
    }

    /** R5-B: construct the typed TimelineSourceBinding from the document clip's
     *  flat source fields. Fully absent source fields yield null (no binding
     *  intent); ANY present source-binding intent must be COMPLETE — partial
     *  fields, unknown sourceKind, or invalid digest FAIL CLOSED (never a
     *  silent null, never catch→null narrowing). */
    private static com.example.platform.timeline.semantics.clip.TimelineSourceBinding typedBindingOf(
            com.example.platform.timeline.canonical.TimelineClip clip, String clipId) {
        String kind = clip.getSourceKind();
        String mediaAssetId = clip.getMediaAssetId();
        String mediaStreamId = clip.getMediaStreamId();
        String artifactId = clip.getArtifactId();
        String contentDigest = clip.getContentDigest();
        // R5-B: source-binding INTENT is the presence of binding-specific
        // fields (mediaStreamId/artifactId/contentDigest). The document model's
        // TimelineClip constructor FORCES sourceKind to "MEDIA_STREAM" when
        // null (legacy default), and mediaAssetId is the clip's generic source
        // reference (always present) — neither alone is binding intent. A clip
        // WITH binding-specific fields but missing pieces fails closed below.
        boolean anyIntent = mediaStreamId != null || artifactId != null
                || contentDigest != null;
        if (!anyIntent) {
            return null; // no authored source-binding intent
        }
        try {
            if (kind == null || kind.isBlank()
                    || !com.example.platform.timeline.semantics.clip.TimelineSourceBinding.SourceKind.MEDIA_STREAM
                            .name().equals(kind)) {
                throw new IllegalStateException(
                        "Unknown TimelineSourceBinding sourceKind: '" + kind + "'");
            }
            if (mediaAssetId == null || mediaAssetId.isBlank()
                    || mediaStreamId == null || mediaStreamId.isBlank()
                    || artifactId == null || artifactId.isBlank()
                    || contentDigest == null || contentDigest.isBlank()) {
                throw new IllegalStateException(
                        "Partial sourceBinding: MEDIA_STREAM requires mediaAssetId, "
                                + "mediaStreamId, artifactId and contentDigest");
            }
            MediaTime rangeStart = clip.getTrimStart() != null ? clip.getTrimStart()
                    : com.example.platform.shared.time.MediaTime.ZERO;
            MediaTime rangeEnd = clip.getTrimEnd() != null ? clip.getTrimEnd()
                    : com.example.platform.shared.time.MediaTime.ZERO;
            if (rangeStart.isGreaterThan(rangeEnd)) {
                throw new IllegalStateException(
                        "Source binding source range start > end");
            }
            return new com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding(
                    new com.example.platform.media.domain.identity.MediaAssetId(mediaAssetId),
                    new com.example.platform.media.domain.stream.MediaStreamId(mediaStreamId),
                    new com.example.platform.shared.identity.ArtifactId(artifactId),
                    com.example.platform.shared.digest.ContentDigest.sha256(contentDigest),
                    new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                            rangeStart, rangeEnd));
        } catch (Exception invalid) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                            TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("sourceBinding"),
                            "Invalid clip sourceBinding: " + invalid.getMessage()));
        }
    }

    private static MediaTime toMediaTime(Duration duration) {
        try {
            return MediaTime.ofRational(duration.toNanos(), 1_000_000_000L);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").field("timing"),
                    "Timeline time value invalid or out of range"));
        }
    }
}
