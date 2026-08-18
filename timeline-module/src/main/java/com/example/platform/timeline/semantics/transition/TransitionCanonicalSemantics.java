package com.example.platform.timeline.semantics.transition;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineTransitionSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): the single
 * Transition-local semantic authority.
 *
 * <p>Owns ONLY Transition-local semantics:
 * <ul>
 *   <li>deterministic canonical value (JSON object — no delimiter grammar)</li>
 *   <li>deterministic fingerprint (SHA-256 over canonical value)</li>
 *   <li>local semantic equality</li>
 *   <li>lossless encode / decode / reconstruction</li>
 * </ul>
 *
 * <p>Timeline keeps aggregate orchestration: transition identity/path, clip
 * participant topology, existence, delete-vs-modify, three-way orchestration,
 * cross-object validation.
 *
 * <p>No generic SemanticComponent framework; this is a bounded authority for
 * exactly one component family. Values such as "a,b=c" survive untouched.
 */
public final class TransitionCanonicalSemantics {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private TransitionCanonicalSemantics() {}

    /** Deterministic canonical JSON value — the single lossless representation. */
    public static ObjectNode canonicalValue(CanonicalTimelineTransitionSnapshot t) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("transitionDefinitionId", t.transitionDefinitionId() == null ? "" : t.transitionDefinitionId());
        node.put("transitionDefinitionVersion", t.transitionDefinitionVersion() == null ? "" : t.transitionDefinitionVersion());
        node.put("outgoingClipId", t.outgoingClipId() == null ? "" : t.outgoingClipId());
        node.put("incomingClipId", t.incomingClipId() == null ? "" : t.incomingClipId());
        node.put("mediaType", t.mediaType() == null ? "" : t.mediaType());
        if (t.duration() != null) {
            node.put("durationTicks", t.duration().ticks());
            node.put("durationTimeScale", t.duration().timeScale());
        }
        node.put("alignment", t.alignment() == null ? "" : t.alignment());
        node.put("temporalPolicy", t.temporalPolicy() == null ? "" : t.temporalPolicy());
        ObjectNode params = node.putObject("parameters");
        Map<String, String> sorted = new TreeMap<>();
        if (t.parameters() != null) {
            sorted.putAll(t.parameters());
        }
        sorted.forEach((k, v) -> params.put(k, v == null ? "" : v));
        return node;
    }

    /** Lossless reconstruction from the canonical value. */
    public static CanonicalTimelineTransitionSnapshot fromCanonicalValue(
            String transitionId, JsonNode node) {
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = node.path("parameters");
        if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(e -> params.put(e.getKey(),
                    e.getValue().isNull() ? "" : e.getValue().asText()));
        }
        MediaTime duration = null;
        if (node.has("durationTicks")) {
            duration = MediaTime.ofTicks(node.path("durationTicks").asLong(),
                    node.path("durationTimeScale").asLong(1));
        }
        return new CanonicalTimelineTransitionSnapshot(
                transitionId,
                node.path("transitionDefinitionId").asText(""),
                node.path("transitionDefinitionVersion").asText("1.0"),
                node.path("outgoingClipId").asText(""),
                node.path("incomingClipId").asText(""),
                node.path("mediaType").asText("VIDEO"),
                duration,
                node.path("alignment").asText("CENTER_ON_CUT"),
                node.path("temporalPolicy").asText("USE_SOURCE_HANDLES"),
                params);
    }

    /** Deterministic fingerprint — SHA-256 over canonical value; no delimiter
     *  collision is possible because the value is structured JSON. */
    public static String semanticFingerprint(CanonicalTimelineTransitionSnapshot t) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(t));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Transition canonical fingerprint failed", e);
        }
    }

    /** Canonical JSON as a string (lossless op payload). */
    public static String encode(CanonicalTimelineTransitionSnapshot t) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(t));
        } catch (Exception e) {
            throw new IllegalStateException("Transition canonical encode failed", e);
        }
    }

    public static boolean localSemanticsEquals(
            CanonicalTimelineTransitionSnapshot a, CanonicalTimelineTransitionSnapshot b) {
        return canonicalValue(a).equals(canonicalValue(b));
    }

    /** R4-A1: mapping between the canonical Transition domain value and the
     *  candidate representation — owned here so no adapter keeps a second
     *  Transition field grammar. */
    public static com.example.platform.timeline.canonicalmodel.CanonicalTransition toCandidateValue(
            CanonicalTimelineTransitionSnapshot t) {
        return new com.example.platform.timeline.canonicalmodel.CanonicalTransition(
                t.transitionId(),
                t.transitionDefinitionId(),
                t.transitionDefinitionVersion(),
                t.outgoingClipId(),
                t.incomingClipId(),
                t.mediaType(),
                t.duration(),
                t.alignment(),
                t.temporalPolicy(),
                t.parameters());
    }
}
