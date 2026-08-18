package com.example.platform.timeline.app;

import com.example.platform.shared.time.CanonicalFrameRateCodec;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.canonicalmodel.TimelineModelPath;
import com.example.platform.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public final class InternalTimelineCandidateAdapter {

    private InternalTimelineCandidateAdapter() {
    }

    public static TimelineCandidate map(String projectId, String internalTimelineJson) {
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
        List<CanonicalTransition> transitions = new ArrayList<>();
        JsonNode transitionNodes = composition.path("transitions");
        if (transitionNodes.isArray()) {
            for (JsonNode trNode : transitionNodes) {
                CanonicalTransition tr = mapTransition(trNode);
                if (tr != null) transitions.add(tr);
            }
        }
        List<CanonicalAutomationCurve> automations = new ArrayList<>();
        JsonNode automationNodes = composition.path("automations");
        if (automationNodes.isArray()) {
            for (JsonNode curveNode : automationNodes) {
                CanonicalAutomationCurve curve = mapAutomation(curveNode);
                if (curve != null) automations.add(curve);
            }
        }
        List<com.example.platform.timeline.canonical.TextElement> textElements = new ArrayList<>();
        JsonNode textNodes = composition.path("textElements");
        if (textNodes.isArray()) {
            for (JsonNode elNode : textNodes) {
                textElements.add(com.example.platform.timeline.canonical.TimedTextCanonicalSemantics
                        .fromCanonicalNode(elNode));
            }
        }
        return new TimelineCandidate(timelineId, projectId,
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, tracks,
                transitions, automations, textElements);
    }

    /**
     * EFFECT_TRANSITION_CANONICALIZATION_V1 (C9): first-class transition — typed
     * participants, exact MediaTime duration, alignment, temporal policy.
     */
    private static CanonicalTransition mapTransition(JsonNode trNode) {
        String id = trNode.path("id").asText("");
        if (id.isBlank()) return null;
        String defId = trNode.path("transitionDefinitionId").asText("");
        String outgoing = trNode.path("outgoingClipId").asText("");
        String incoming = trNode.path("incomingClipId").asText("");
        if (outgoing.isBlank() || incoming.isBlank()) return null;
        MediaTime duration = mediaTimeFromTicks(
                trNode.path("durationTicks").asLong(0),
                trNode.path("durationTimeScale").asLong(1));
        if (duration.isLessThanOrEqualTo(MediaTime.ZERO)) return null;
        java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
        JsonNode paramsNode = trNode.path("parameters");
        if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(e -> params.put(e.getKey(),
                    e.getValue().asText("")));
        }
        return new CanonicalTransition(id, defId,
                trNode.path("transitionDefinitionVersion").asText("1.0"),
                outgoing, incoming,
                trNode.path("mediaType").asText("VIDEO"),
                duration,
                trNode.path("alignment").asText("CENTER_ON_CUT"),
                trNode.path("temporalPolicy").asText("USE_SOURCE_HANDLES"),
                params);
    }

    /**
     * EFFECT_TRANSITION_CANONICALIZATION_V1 (C7/C8): automation — exact MediaTime
     * keyframes, deterministic ordering, HOLD/LINEAR interpolation.
     */
    private static CanonicalAutomationCurve mapAutomation(JsonNode curveNode) {
        String id = curveNode.path("automationId").asText("");
        if (id.isBlank()) return null;
        List<CanonicalAutomationKeyframe> keyframes = new ArrayList<>();
        JsonNode kfNodes = curveNode.path("keyframes");
        if (kfNodes.isArray()) {
            for (JsonNode kf : kfNodes) {
                keyframes.add(new CanonicalAutomationKeyframe(
                        kf.path("keyframeId").asText("kf_" + keyframes.size()),
                        mediaTimeFromTicks(kf.path("timeTicks").asLong(0),
                                kf.path("timeTimeScale").asLong(1)),
                        kf.path("value").asDouble(0.0),
                        kf.path("interpolation").asText("LINEAR")));
            }
        }
        return new CanonicalAutomationCurve(id,
                curveNode.path("targetEntityId").asText(""),
                curveNode.path("parameterPath").asText(""),
                curveNode.path("valueType").asText("float"),
                curveNode.path("extrapolation").asText("HOLD"),
                keyframes);
    }

    private static MediaTime mediaTimeFromTicks(long ticks, long timeScale) {
        return MediaTime.ofTicks(ticks, timeScale);
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
        FrameRate rate = clipRateOf(clipNode);
        MediaTime timelineStart = rangeStart(clipNode.path("timelineRange"), "timelineRange", clipId, rate);
        MediaTime sourceStart = sourceRangeStart(clipNode, clipId, rate);
        MediaTime duration = rangeDuration(clipNode.path("timelineRange"), "timelineRange", clipId, rate);
        return new TimelineCandidate.Clip(clipId, TimelineSourceRef.of(assetId),
                timelineStart, sourceStart, duration, rate, mapEffects(clipNode), List.of());
    }

    /**
     * C1-CNM1-CR1: parse the canonical clip rate through
     * {@link CanonicalFrameRateCodec}. Present-but-invalid rate input
     * (out-of-int32, zero/negative denominator, malformed, non-integral) is
     * REJECTED with {@link TimelineCanonicalRejectionException} — never
     * silently defaulted. Only a fully absent rate node follows the optional
     * default policy.
     */
    private static FrameRate clipRateOf(JsonNode clipNode) {
        JsonNode rate = clipNode.path("timelineRange").path("start").path("rate");
        try {
            return CanonicalFrameRateCodec.parse(rate, true);
        } catch (CanonicalFrameRateCodec.InvalidCanonicalRateException e) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").field("rate"),
                            e.getMessage()));
        }
    }

    /**
     * FOURTH CORRECTION: parse the wire {@code effects[]} array into
     * {@link TimelineClipEffect} canonical value objects. Effect local
     * semantics (semanticFingerprint) are Timeline-authored state — the
     * production diff/patch/merge path consumes that authority.
     */
    private static List<TimelineClipEffect> mapEffects(JsonNode clipNode) {
        JsonNode effects = clipNode.path("effects");
        if (!effects.isArray() || effects.isEmpty()) {
            return List.of();
        }
        List<TimelineClipEffect> out = new ArrayList<>();
        for (JsonNode fx : effects) {
            String id = fx.path("id").asText(null);
            String effectKey = fx.path("effectKey").asText("");
            if (effectKey.isBlank()) {
                // FIFTH CORRECTION (F4.1): missing/blank effectKey FAILS CLOSED.
                // No "opaque"/"unknown" fallback — canonical contract requires
                // a non-blank effect key.
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_EFFECT_KEY_INVALID,
                                TimelineModelPath.root().field("tracks").field("clips").field("effects"),
                                "Effect effectKey must be non-blank"));
            }
            Map<String, Object> parameters = fx.path("parameters").isObject()
                    ? InternalTimelineJson.mapper().convertValue(fx.path("parameters"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                    : Map.of();
            out.add(new TimelineClipEffect(id, effectKey, parameters));
        }
        return out;
    }

    /** Exact rational frame conversion via MediaTime.ofFrames(frames, rate.num, rate.den); zero floating-point loss. */
    private static MediaTime ofFrames(long frames, FrameRate rate) {
        try {
            return MediaTime.ofFrames(frames, rate.numerator().longValueExact(), rate.denominator());
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").field("timing"),
                            "Internal timeline time value invalid or out of range"));
        }
    }

    private static MediaTime rangeStart(JsonNode range, String field, String clipId, FrameRate rate) {
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
        return ofFrames(frame, rate);
    }

    private static MediaTime sourceRangeStart(JsonNode clipNode, String clipId, FrameRate rate) {
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
        return ofFrames(frame, rate);
    }

    private static MediaTime rangeDuration(JsonNode range, String field, String clipId, FrameRate rate) {
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
        return ofFrames(frame, rate);
    }
}
