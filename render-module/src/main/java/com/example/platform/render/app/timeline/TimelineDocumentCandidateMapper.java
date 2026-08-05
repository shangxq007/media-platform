package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineModelPath;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
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
final class TimelineDocumentCandidateMapper {

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
        return new TimelineCandidate(productId, productId,
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, tracks);
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
        String clipId = clip.getClipId();
        if (clipId == null || clipId.isBlank() || !clipId.equals(clipId.strip())) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").field("clipId"),
                    "Clip identifier must be nonblank and already normalized"));
        }
        String assetId = clip.getAssetId();
        if (assetId == null || assetId.isBlank() || !assetId.equals(assetId.strip())) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("sourceRef"),
                    "Source reference (assetId) must be nonblank and already normalized"));
        }
        MediaTime timelineStart = toMediaTime(clip.getStartTime());
        MediaTime sourceStart = toMediaTime(clip.getTrimStart());
        MediaTime duration;
        try {
            Duration diff = clip.getEndTime().minus(clip.getStartTime());
            if (diff.isNegative()) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                                TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("duration"),
                                "Clip endTime must not precede startTime"));
            }
            duration = toMediaTime(diff);
        } catch (ArithmeticException overflow) {
            throw new TimelineCanonicalRejectionException(new TimelineCanonicalRejectionException.AdapterDiagnostic(
                    TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                    TimelineModelPath.root().field("tracks").field("clips").id(clipId).field("duration"),
                    "Clip duration overflow"));
        }
        return new TimelineCandidate.Clip(clipId, TimelineSourceRef.of(assetId),
                timelineStart, sourceStart, duration, List.of());
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
