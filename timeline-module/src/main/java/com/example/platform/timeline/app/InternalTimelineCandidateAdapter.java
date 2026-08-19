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
                transitions, automations, textElements,
                com.example.platform.timeline.app.InternalTimelineCandidateAdapter.AudioMixJson.audioMixOf(composition),
                com.example.platform.timeline.app.InternalTimelineCandidateAdapter.RelationshipJson.relationshipsOf(composition));
    }

    /** CHECKPOINT_A: parse the authored AudioMix from the internal payload (absent == empty).
     *  R4-A4: decode delegates to the Audio-domain authority; this adapter is a thin
     *  boundary (no AudioMasterBus/AudioRoute/gain/mute/balance/DSP field knowledge). */
    public static final class AudioMixJson {
        private static final com.fasterxml.jackson.databind.ObjectMapper M =
                com.example.platform.timeline.app.InternalTimelineJson.mapper();

        public static com.example.platform.audio.domain.mix.AudioMix audioMixOf(JsonNode composition) {
            JsonNode node = composition.path("audioMix");
            if (!node.isObject() || node.isNull() || node.isEmpty()) {
                return com.example.platform.audio.domain.mix.AudioMix.empty();
            }
            try {
                return com.example.platform.audio.domain.mix.AudioMixCanonicalSemantics
                        .fromCanonicalJson(node);
            } catch (Exception e) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                TimelineModelPath.root().field("composition").field("audioMix"),
                                "Malformed authored AudioMix: " + e.getMessage()));
            }
        }
    }

    /** CHECKPOINT_A: parse authored SemanticRelationships (absent == empty). */
    static final class RelationshipJson {
        static java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> relationshipsOf(
                JsonNode composition) {
            JsonNode node = composition.path("semanticRelationships");
            java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> out = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode rel : node) {
                    try {
                        out.add(com.example.platform.timeline.app.InternalTimelineJson.mapper()
                                .treeToValue(rel, com.example.platform.timeline.semantics.relationship.SemanticRelationship.class));
                    } catch (Exception e) {
                        throw new TimelineCanonicalRejectionException(
                                new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                        TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                        TimelineModelPath.root().field("composition").field("semanticRelationships"),
                                        "Malformed authored SemanticRelationship: " + e.getMessage()));
                    }
                }
            }
            return out;
        }
    }

    /**
     * EFFECT_TRANSITION_CANONICALIZATION_V1 (C9): first-class transition — typed
     * participants, exact MediaTime duration, alignment, temporal policy.
     *
     * R4-A1: authored field decoding delegated to TransitionCanonicalSemantics
     * (single Transition-local grammar). Timeline keeps only aggregate topology
     * validation: non-blank identity, participant presence, duration > zero.
     */
    private static CanonicalTransition mapTransition(JsonNode trNode) {
        String id = trNode.path("id").asText("");
        if (id.isBlank()) return null;
        String outgoing = trNode.path("outgoingClipId").asText("");
        String incoming = trNode.path("incomingClipId").asText("");
        if (outgoing.isBlank() || incoming.isBlank()) return null;
        // R5-A: the authority decodes into the DOMAIN value CanonicalTransition
        // (strict; missing/malformed REQUIRED authored fields fail closed).
        CanonicalTransition decoded =
                com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics
                        .fromCanonicalValue(id, trNode);
        if (decoded.duration() == null || decoded.duration().isLessThanOrEqualTo(MediaTime.ZERO)) {
            return null;
        }
        return decoded;
    }

    /**
     * EFFECT_TRANSITION_CANONICALIZATION_V1 (C7/C8): automation — exact MediaTime
     * keyframes, deterministic ordering, HOLD/LINEAR interpolation.
     *
     * R4-A2: authored field decoding delegated to AutomationCanonicalSemantics
     * (single Automation-local grammar). Timeline keeps only aggregate topology
     * validation: non-blank automation identity.
     */
    private static CanonicalAutomationCurve mapAutomation(JsonNode curveNode) {
        String id = curveNode.path("automationId").asText("");
        if (id.isBlank()) return null;
        // R5-A: the authority decodes into the DOMAIN value
        // CanonicalAutomationCurve (strict; missing/malformed REQUIRED
        // authored fields fail closed).
        return com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics
                .fromCanonicalValue(id, curveNode);
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
        // CHECKPOINT_A: carry the full typed source semantics of the clip
        // (kind/asset/stream/artifact/digest/temporal mapping).
        // R4-B: the TYPED TimelineSourceBinding is the merge-path authority;
        // the nested sourceBinding object (extractor-compatible wire shape) is
        // the canonical wire form.
        // R5-B: flat wire fields (sourceKind/mediaStreamId/artifactId/
        // contentDigest) are canonicalized IMMEDIATELY into ONE typed
        // TimelineSourceBinding below — no flat semantic state survives into
        // TimelineCandidate.Clip (single typed source authority; no dual
        // contradictory representation). Partial/missing source binding intent
        // FAILS CLOSED (never catch→null narrowing).
        com.example.platform.timeline.semantics.clip.TimelineSourceBinding sourceBinding =
                sourceBindingOf(clipNode, clipId);
        com.example.platform.timeline.semantics.temporal.TemporalMapping temporalMapping = null;
        JsonNode tmNode = clipNode.path("temporalMapping");
        if (tmNode.isObject() && !tmNode.isEmpty()) {
            try {
                temporalMapping = com.example.platform.timeline.app.InternalTimelineJson.mapper()
                        .treeToValue(tmNode, com.example.platform.timeline.semantics.temporal.TemporalMapping.class);
            } catch (Exception e) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("temporalMapping"),
                                "Malformed clip temporalMapping: " + e.getMessage()));
            }
        }
        return new TimelineCandidate.Clip(clipId, TimelineSourceRef.of(assetId),
                timelineStart, sourceStart, duration, rate, mapEffects(clipNode), List.of(),
                temporalMapping, sourceBinding);
    }

    /**
     * R5-B: parse the clip's typed source binding from the canonical wire.
     * Nested {@code sourceBinding} object is the canonical form; legacy flat
     * fields (sourceKind/mediaStreamId/artifactId/contentDigest on the clip
     * node) are canonicalized immediately into the same typed value. If ANY
     * source-binding intent is present it must be COMPLETE — partial binding
     * (e.g. artifactId without mediaStreamId, unknown sourceKind, malformed
     * digest) FAILS CLOSED via TimelineCanonicalRejectionException. Only a
     * fully ABSENT sourceBinding (no nested object AND no flat source fields)
     * yields a null binding.
     */
    private static com.example.platform.timeline.semantics.clip.TimelineSourceBinding sourceBindingOf(
            JsonNode clipNode, String clipId) {
        // POST_FINAL_REVIEW_P2-A: ABSENT vs PRESENT must be distinct. A
        // PRESENT sourceBinding field is authored binding intent and MUST be
        // structurally valid — null / string / array / number / boolean /
        // empty object / non-object PRESENT input FAILS CLOSED (never silently
        // falls through to flat detection → null absence).
        if (clipNode.has("sourceBinding")) {
            JsonNode sbNode = clipNode.get("sourceBinding");
            if (sbNode == null || sbNode.isNull() || !sbNode.isObject() || sbNode.isEmpty()) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("sourceBinding"),
                                "PRESENT clip sourceBinding must be a non-empty object (got "
                                        + (sbNode == null ? "null" : sbNode.getNodeType().name()) + ")"));
            }
            try {
                // F2 legacy-alias boundary (clearly identified): the historical
                // internal-1.0 wire used "SHA256" (no underscore); the canonical
                // domain algorithm value is "SHA_256". The canonical decoder
                // (TimelineSourceBindingCanonicalSemantics.fromCanonicalValue)
                // is STRICT and accepts ONLY canonical values — this adapter
                // boundary canonicalizes the single known legacy alias before
                // delegating. No general digest compatibility framework.
                JsonNode effective = sbNode;
                JsonNode algoNode = sbNode.path("contentDigest").path("algorithm");
                if (algoNode.isTextual() && "SHA256".equals(algoNode.asText())) {
                    com.fasterxml.jackson.databind.node.ObjectNode copy = sbNode.deepCopy();
                    ((com.fasterxml.jackson.databind.node.ObjectNode) copy.path("contentDigest"))
                            .put("algorithm", "SHA_256");
                    effective = copy;
                }
                return com.example.platform.timeline.semantics.clip
                        .TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(effective);
            } catch (Exception e) {
                throw new TimelineCanonicalRejectionException(
                        new TimelineCanonicalRejectionException.AdapterDiagnostic(
                                TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                                TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("sourceBinding"),
                                "Malformed clip sourceBinding: " + e.getMessage()));
            }
        }
        // Legacy flat fields: canonicalize immediately into the typed binding.
        String flatSourceKind = clipNode.path("sourceKind").asText(null);
        String flatMediaStreamId = clipNode.path("mediaStreamId").asText(null);
        String flatArtifactId = clipNode.path("artifactId").asText(null);
        String flatContentDigest = clipNode.path("contentDigest").asText(null);
        String flatAssetId = clipNode.path("assetId").asText(null);
        String flatMediaAssetId = clipNode.path("mediaAssetId").asText(null);
        // R5-B: binding INTENT = presence of binding-specific fields
        // (mediaAssetId/mediaStreamId/artifactId/contentDigest). sourceKind
        // alone is not intent (legacy payloads may carry a lone sourceKind
        // projection). A clip WITH any binding field but missing pieces fails
        // closed below — no MediaAssetId-only fallback.
        boolean anyFlatIntent = flatMediaAssetId != null || flatMediaStreamId != null
                || flatArtifactId != null || flatContentDigest != null;
        if (!anyFlatIntent) {
            return null; // no authored source-binding intent
        }
        try {
            return com.example.platform.timeline.semantics.clip
                    .TimelineSourceBindingCanonicalSemantics.fromFlatFields(
                            flatSourceKind, flatAssetId, flatMediaStreamId,
                            flatArtifactId, flatContentDigest, clipNode.path("sourceRange"));
        } catch (Exception e) {
            throw new TimelineCanonicalRejectionException(
                    new TimelineCanonicalRejectionException.AdapterDiagnostic(
                            TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                            TimelineModelPath.root().field("composition").field("tracks").field("clips").id(clipId).field("sourceBinding"),
                            "Partial/invalid clip sourceBinding (flat): " + e.getMessage()));
        }
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
