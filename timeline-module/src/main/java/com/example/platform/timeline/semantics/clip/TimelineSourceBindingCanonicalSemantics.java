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
     *  CLOSED — never a silent blank binding. */
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
        ContentDigest digest = new ContentDigest(
                ContentDigest.DigestAlgorithm.SHA_256,
                digestNode.path("value").asText(""));
        MediaTime start = MediaTime.parse(node.path("sourceRangeStart").asText("0"));
        MediaTime end = MediaTime.parse(node.path("sourceRangeEnd").asText("0"));
        return new MediaStreamSourceBinding(
                new MediaAssetId(node.path("mediaAssetId").asText("")),
                new MediaStreamId(node.path("mediaStreamId").asText("")),
                new ArtifactId(node.path("artifactId").asText("")),
                digest,
                new MediaClip.TimeRange(start, end));
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
