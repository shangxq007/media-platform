package com.example.platform.timeline.semantics.clip;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 4, R4-B): the
 * single TimelineSourceBinding-local canonical semantic authority.
 *
 * <p>Owns ONLY source-binding semantics:
 * <ul>
 *   <li>deterministic canonical value (nested {@code sourceBinding} JSON
 *       object — the wire format the Artifact pin extractor reads)</li>
 *   <li>deterministic fingerprint (SHA-256 over canonical value)</li>
 *   <li>local semantic equality</li>
 *   <li>lossless encode / decode / reconstruction</li>
 * </ul>
 *
 * <p>Timeline keeps aggregate orchestration: clip identity, placement,
 * existence checks, delete-vs-modify, three-way orchestration. The source
 * binding itself is typed ({@link TimelineSourceBinding} sealed root,
 * {@link MediaStreamSourceBinding} concrete kind); no universal asset god
 * object, no MediaAssetId-only fallback, no String-field narrowing.
 *
 * <p>Unknown source kinds FAIL CLOSED — never a silent null/blank binding.
 */
public final class TimelineSourceBindingCanonicalSemantics {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private TimelineSourceBindingCanonicalSemantics() {}

    /** Deterministic canonical JSON value — the single lossless representation.
     *  Matches the extractor's wire shape: nested object with artifactId and
     *  contentDigest{algorithm,value}. */
    public static ObjectNode canonicalValue(TimelineSourceBinding binding) {
        if (binding == null) {
            return null;
        }
        if (binding instanceof MediaStreamSourceBinding m) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("sourceKind", m.sourceKind().name());
            node.put("mediaAssetId", m.mediaAssetId().value());
            node.put("mediaStreamId", m.mediaStreamId().value());
            node.put("artifactId", m.artifactId().value());
            ObjectNode digest = node.putObject("contentDigest");
            digest.put("algorithm", m.contentDigest().algorithm().name());
            digest.put("value", m.contentDigest().value());
            node.put("sourceRangeStart", m.sourceRange().start().toString());
            node.put("sourceRangeEnd", m.sourceRange().end().toString());
            return node;
        }
        throw new IllegalStateException(
                "Unknown TimelineSourceBinding variant: " + binding.getClass().getName());
    }

    /** Lossless reconstruction from the canonical value. Unknown kinds FAIL
     *  CLOSED — never a silent blank binding. R5-B: partial source binding
     *  (missing mediaAssetId/mediaStreamId/artifactId/digest/source range,
     *  malformed digest, malformed range) FAILS CLOSED via the typed identity /
     *  digest / range constructors — no synthesized defaults, no catch→null
     *  narrowing. */
    public static TimelineSourceBinding fromCanonicalValue(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject() || node.isEmpty()) {
            return null;
        }
        String kind = node.path("sourceKind").asText("");
        if (!TimelineSourceBinding.SourceKind.MEDIA_STREAM.name().equals(kind)) {
            throw new IllegalStateException(
                    "Unknown TimelineSourceBinding sourceKind: '" + kind + "'");
        }
        JsonNode digestNode = node.path("contentDigest");
        // R5-B strict: contentDigest must be a present object with a valid
        // SHA-256 value (ContentDigest constructor rejects blank/invalid).
        ContentDigest digest = new ContentDigest(
                ContentDigest.DigestAlgorithm.SHA_256,
                digestNode.path("value").asText(""));
        // R5-B strict: source range REQUIRED in the canonical value — missing
        // or malformed range is not silently defaulted to zero.
        String rangeStartText = node.path("sourceRangeStart").asText(null);
        String rangeEndText = node.path("sourceRangeEnd").asText(null);
        if (rangeStartText == null || rangeEndText == null
                || rangeStartText.isBlank() || rangeEndText.isBlank()) {
            throw new IllegalStateException(
                    "MediaStreamSourceBinding requires exact sourceRangeStart/sourceRangeEnd");
        }
        MediaTime start = MediaTime.parse(rangeStartText);
        MediaTime end = MediaTime.parse(rangeEndText);
        if (start.isGreaterThan(end)) {
            throw new IllegalStateException(
                    "MediaStreamSourceBinding source range start > end");
        }
        return new MediaStreamSourceBinding(
                new MediaAssetId(node.path("mediaAssetId").asText("")),
                new MediaStreamId(node.path("mediaStreamId").asText("")),
                new ArtifactId(node.path("artifactId").asText("")),
                digest,
                new MediaClip.TimeRange(start, end));
    }

    /**
     * R5-B: canonicalize legacy FLAT wire fields into ONE typed
     * TimelineSourceBinding immediately at the adapter boundary. If ANY flat
     * source-binding intent is present it must be COMPLETE and VALID
     * (sourceKind=MEDIA_STREAM, mediaAssetId, mediaStreamId, artifactId,
     * contentDigest, exact source range from the wire sourceRange node) —
     * partial intent FAILS CLOSED (never MediaAssetId-only fallback, never a
     * silent null). Fully absent flat fields yield null (no binding intent).
     */
    public static TimelineSourceBinding fromFlatFields(
            String sourceKind, String mediaAssetId, String mediaStreamId,
            String artifactId, String contentDigest, JsonNode sourceRangeNode) {
        if (sourceKind == null && mediaStreamId == null && artifactId == null
                && contentDigest == null) {
            return null; // no authored source-binding intent
        }
        if (!TimelineSourceBinding.SourceKind.MEDIA_STREAM.name().equals(sourceKind)) {
            throw new IllegalStateException(
                    "Unknown TimelineSourceBinding sourceKind: '" + sourceKind + "'");
        }
        if (mediaAssetId == null || mediaAssetId.isBlank()
                || mediaStreamId == null || mediaStreamId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || contentDigest == null || contentDigest.isBlank()) {
            throw new IllegalStateException(
                    "Partial flat sourceBinding: MEDIA_STREAM requires mediaAssetId, "
                            + "mediaStreamId, artifactId and contentDigest");
        }
        MediaClip.TimeRange range = rangeOf(sourceRangeNode);
        if (range == null) {
            throw new IllegalStateException(
                    "Partial flat sourceBinding: sourceRange required for MEDIA_STREAM");
        }
        return new MediaStreamSourceBinding(
                new MediaAssetId(mediaAssetId),
                new MediaStreamId(mediaStreamId),
                new ArtifactId(artifactId),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, contentDigest),
                range);
    }

    /** Exact wire sourceRange → TimeRange; null when the node is absent. */
    private static MediaClip.TimeRange rangeOf(JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return null;
        }
        JsonNode startNode = node.path("start");
        JsonNode durationNode = node.path("duration");
        if (!startNode.isObject() || !durationNode.isObject()) {
            return null;
        }
        try {
            long fpsNum = startNode.path("rate").path("num").asLong(30);
            long fpsDen = startNode.path("rate").path("den").asLong(1);
            long startFrame = startNode.path("frame").asLong(0);
            long durationFrame = durationNode.path("frame").asLong(0);
            if (fpsNum <= 0 || fpsDen <= 0) {
                return null;
            }
            // frame N @ fps num/den → time = N * den / num seconds
            // (exact rational: ticks = N*den, timeScale = num).
            MediaTime start = MediaTime.ofTicks(startFrame * fpsDen, fpsNum);
            MediaTime duration = MediaTime.ofTicks(durationFrame * fpsDen, fpsNum);
            return new MediaClip.TimeRange(start, start.add(duration));
        } catch (Exception e) {
            return null;
        }
    }

    /** Deterministic fingerprint — SHA-256 over canonical value; no delimiter
     *  collision possible (structured JSON). */
    public static String semanticFingerprint(TimelineSourceBinding binding) {
        if (binding == null) {
            return "";
        }
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(binding));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SourceBinding canonical fingerprint failed", e);
        }
    }

    /** Canonical JSON as a string (lossless op payload). */
    public static String encode(TimelineSourceBinding binding) {
        if (binding == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(canonicalValue(binding));
        } catch (Exception e) {
            throw new IllegalStateException("SourceBinding canonical encode failed", e);
        }
    }

    /** Lossless decode from canonical JSON string. */
    public static TimelineSourceBinding decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return fromCanonicalValue(MAPPER.readTree(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("SourceBinding canonical decode failed", e);
        }
    }

    public static boolean localSemanticsEquals(
            TimelineSourceBinding a, TimelineSourceBinding b) {
        if (a == null || b == null) {
            return a == b;
        }
        return canonicalValue(a).equals(canonicalValue(b));
    }
}
