package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineModelPath;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Contract G adapter (PTCSG_RECORD_REVISION_CANONICAL_GATE_E1B_V1): maps the internal-1.0
 * JSON representation to a {@link TimelineCandidate} for the E1b canonical gate.
 *
 * <p>Frozen by PTADTF-C contract-g-adapter-decision and contract-g-field-mapping.tsv:
 * single adapter for the internal-1.0 source format; internal and pure (no repository,
 * network, current-time, or randomness access); deterministic; EXACT rational MediaTime
 * conversion ({@link MediaTime#ofFrames} - no floating point); exactly the five frozen
 * application rejection codes mapped onto internal-1.0 failure semantics; no new codes;
 * zero silently dropped semantic fields (revision counter and metadata/styles/templates/
 * assetRegistry are representation-level and documented as non-semantic).</p>
 */
final class InternalTimelineCandidateAdapter {

    private static final int DEFAULT_FPS = 30;

    private InternalTimelineCandidateAdapter() {
    }

    static TimelineCandidate map(String projectId, String internalTimelineJson) {
        JsonNode root;
        try {
            root = InternalTimelineJson.parse(internalTimelineJson);
        } catch (Exception e) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                            TimelineModelPath.root().field("schemaVersion"),
                            "Malformed internal-1.0 JSON: cannot parse"));
        }
        if (!InternalTimelineJson.isInternalTimeline(root)) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                            TimelineModelPath.root().field("schemaVersion"),
                            "Unsupported internal timeline schema"));
        }
        String timelineId = root.path("id").asText("");
        if (timelineId.isBlank() || !timelineId.equals(timelineId.strip())) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                            TimelineModelPath.root().field("id"),
                            "Timeline identifier must be nonblank and already normalized"));
        }
        JsonNode composition = root.path("composition");
        if (!composition.isObject()) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                            TimelineModelPath.root().field("composition"),
                            "Internal-1.0 composition block is missing"));
        }
        List<TimelineCandidate.Track> tracks = new ArrayList<>();
        JsonNode trackNodes = composition.path("tracks");
        if (trackNodes.isArray()) {
            for (JsonNode trackNode : trackNodes) {
                tracks.add(mapTrack(trackNode));
            }
        }
        return new TimelineCandidate(timelineId, projectId,
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, tracks);
    }

    private static TimelineCandidate.Track mapTrack(JsonNode trackNode) {
        String trackId = trackNode.path("id").asText("");
        if (trackId.isBlank() || !trackId.equals(trackId.strip())) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").id("unknown").field("id"),
                            "Track identifier must be nonblank and already normalized"));
        }
        TimelineCandidate.TrackType type = mapType(trackNode.path("type").asText("VIDEO"), trackId);
        int zOrder = trackNode.path("zIndex").asInt(0);
        List<TimelineCandidate.Clip> clips = new ArrayList<>();
        JsonNode clipNodes = trackNode.path("clips");
        if (clipNodes.isArray()) {
            for (JsonNode clipNode : clipNodes) {
                clips.add(mapClip(clipNode));
            }
        }
        return new TimelineCandidate.Track(trackId, type, zOrder, null, clips);
    }

    private static TimelineCandidate.TrackType mapType(String type, String trackId) {
        if (type == null || type.isBlank()) {
            return TimelineCandidate.TrackType.VIDEO; // existing convention: null type defaults to VIDEO
        }
        return switch (type) {
            case "VIDEO" -> TimelineCandidate.TrackType.VIDEO;
            case "AUDIO" -> TimelineCandidate.TrackType.AUDIO;
            default -> throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                            TimelineModelPath.root().field("composition").field("tracks").id(trackId).field("type"),
                            "Unsupported internal track type: " + type));
        };
    }

    private static TimelineCandidate.Clip mapClip(JsonNode clipNode) {
        String clipId = clipNode.path("id").asText("");
        if (clipId.isBlank() || !clipId.equals(clipId.strip())) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").field("clipId"),
                            "Clip identifier must be nonblank and already normalized"));
        }
        String assetId = clipNode.path("assetId").asText("");
        if (assetId.isBlank() || !assetId.equals(assetId.strip())) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("sourceRef"),
                            "Source reference (assetId) must be nonblank and already normalized"));
        }
        int fps = frameRateOf(clipNode);
        MediaTime timelineStart = rangeStart(clipNode.path("timelineRange"), "timelineRange", clipId, fps);
        MediaTime sourceStart = sourceRangeStart(clipNode, clipId, fps);
        MediaTime duration = rangeDuration(clipNode.path("timelineRange"), "timelineRange", clipId, fps);
        return new TimelineCandidate.Clip(clipId, TimelineSourceRef.of(assetId),
                timelineStart, sourceStart, duration, List.of());
    }

    /** Exact rational frame conversion via MediaTime.ofFrames; zero floating-point loss. */
    private static MediaTime ofFrames(int frames, int fps) {
        try {
            return MediaTime.ofFrames(frames, fps, 1);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").field("timing"),
                            "Internal timeline time value invalid or out of range"));
        }
    }

    private static int frameRateOf(JsonNode node) {
        JsonNode rate = node.path("rate");
        if (rate.has("num") && rate.has("den") && rate.get("num").asInt(0) > 0 && rate.get("den").asInt(1) > 0) {
            return rate.get("num").asInt();
        }
        return DEFAULT_FPS;
    }

    private static MediaTime rangeStart(JsonNode range, String field, String clipId, int fps) {
        if (range.isMissingNode() || range.isNull()) {
            return MediaTime.ZERO; // missing range start defaults to zero (existing convention)
        }
        int frame = range.path("start").path("frame").asInt(-1);
        if (frame < 0) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field(field),
                            "Clip timing frame must be non-negative"));
        }
        int rate = range.path("start").path("rate").has("num")
                ? range.path("start").path("rate").get("num").asInt(fps) : fps;
        return ofFrames(frame, rate);
    }

    private static MediaTime sourceRangeStart(JsonNode clipNode, String clipId, int fps) {
        JsonNode sourceRange = clipNode.path("sourceRange");
        if (sourceRange.isMissingNode() || sourceRange.isNull()) {
            return MediaTime.ZERO;
        }
        int frame = sourceRange.path("start").path("frame").asInt(-1);
        if (frame < 0) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("sourceRange"),
                            "Clip source range frame must be non-negative"));
        }
        int rate = sourceRange.path("start").path("rate").has("num")
                ? sourceRange.path("start").path("rate").get("num").asInt(fps) : fps;
        return ofFrames(frame, rate);
    }

    private static MediaTime rangeDuration(JsonNode range, String field, String clipId, int fps) {
        if (range.isMissingNode() || range.isNull()) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field(field),
                            "Clip timing range is missing"));
        }
        int frame = range.path("duration").path("frame").asInt(-1);
        if (frame <= 0) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field(field),
                            "Clip duration must be positive"));
        }
        int rate = range.path("duration").path("rate").has("num")
                ? range.path("duration").path("rate").get("num").asInt(fps) : fps;
        return ofFrames(frame, rate);
    }
}
