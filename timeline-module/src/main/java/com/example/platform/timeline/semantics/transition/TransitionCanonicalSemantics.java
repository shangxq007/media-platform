package com.example.platform.timeline.semantics.transition;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
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
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3; R5-A: authority
 * re-homed on the canonical DOMAIN value {@link CanonicalTransition}): the
 * single Transition-local semantic authority.
 *
 * <p>The canonical semantic contract is defined over the domain value
 * {@link CanonicalTransition} (canonicalmodel) — NOT over the central
 * diff/merge snapshot projection {@link CanonicalTimelineTransitionSnapshot}.
 * The snapshot is only merge transport: {@link #toSnapshotValue} /
 * {@link #fromSnapshotValue} project to/from it; it never defines the
 * Transition canonical semantic contract.
 *
 * <p>Owns ONLY Transition-local semantics:
 * <ul>
 *   <li>deterministic canonical value (JSON object — no delimiter grammar)</li>
 *   <li>deterministic fingerprint (SHA-256 over canonical value)</li>
 *   <li>local semantic equality</li>
 *   <li>lossless encode / STRICT decode / reconstruction</li>
 *   <li>conversion to/from the canonical Transition domain value</li>
 * </ul>
 *
 * <p>STRICT decode (R5-A): a canonical authored Transition payload requires
 * every authored field — transitionDefinitionId, transitionDefinitionVersion,
 * outgoingClipId, incomingClipId, mediaType, durationTicks, durationTimeScale,
 * alignment, temporalPolicy. Missing or malformed REQUIRED fields FAIL CLOSED;
 * the decoder never synthesizes authored semantics (no default version/media
 * type/alignment/policy, no implicit timeScale). Parameters may be empty only
 * because empty parameters are valid authored semantics.
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

    // ── Canonical value over the DOMAIN value (R5-A authority) ────────────

    /** Deterministic canonical JSON value — the single lossless representation
     *  of the canonical Transition domain value. */
    public static ObjectNode canonicalValue(CanonicalTransition t) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("transitionDefinitionId", t.transitionDefinitionId());
        node.put("transitionDefinitionVersion", t.transitionDefinitionVersion());
        node.put("outgoingClipId", t.outgoingClipId());
        node.put("incomingClipId", t.incomingClipId());
        node.put("mediaType", t.mediaType());
        node.put("durationTicks", t.duration().ticks());
        node.put("durationTimeScale", t.duration().timeScale());
        node.put("alignment", t.alignment());
        node.put("temporalPolicy", t.temporalPolicy());
        ObjectNode params = node.putObject("parameters");
        Map<String, String> sorted = new TreeMap<>();
        if (t.parameters() != null) {
            sorted.putAll(t.parameters());
        }
        sorted.forEach((k, v) -> params.put(k, v == null ? "" : v));
        return node;
    }

    /** STRICT lossless reconstruction from the canonical value (R5-A). Every
     *  REQUIRED authored field must be present and valid — missing/malformed
     *  fields FAIL CLOSED (IllegalArgumentException). No synthesized defaults:
     *  no implicit version/mediaType/alignment/temporalPolicy, no implicit
     *  durationTimeScale. {@code transitionId} is Timeline-owned aggregate
     *  identity (the path), supplied by the caller. */
    public static CanonicalTransition fromCanonicalValue(String transitionId, JsonNode node) {
        String definitionId = requiredText(node, "transitionDefinitionId");
        String definitionVersion = requiredText(node, "transitionDefinitionVersion");
        String outgoingClipId = requiredText(node, "outgoingClipId");
        String incomingClipId = requiredText(node, "incomingClipId");
        String mediaType = requiredText(node, "mediaType");
        String alignment = requiredText(node, "alignment");
        String temporalPolicy = requiredText(node, "temporalPolicy");
        if (!node.has("durationTicks") || !node.has("durationTimeScale")) {
            throw new IllegalArgumentException(
                    "Transition requires durationTicks and durationTimeScale");
        }
        // F2 (post-Round-5): integral JSON nodes REQUIRED — no Jackson
        // coercion of strings/booleans/objects/arrays into numbers.
        JsonNode ticksNode = node.path("durationTicks");
        JsonNode scaleNode = node.path("durationTimeScale");
        if (!ticksNode.isIntegralNumber() || !scaleNode.isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "Transition durationTicks/durationTimeScale must be integral JSON numbers");
        }
        long ticks = ticksNode.asLong();
        long timeScale = scaleNode.asLong();
        if (ticks <= 0 || timeScale <= 0) {
            throw new IllegalArgumentException(
                    "Transition duration must be positive (ticks=" + ticks
                            + ", timeScale=" + timeScale + ")");
        }
        MediaTime duration = MediaTime.ofTicks(ticks, timeScale);
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = node.path("parameters");
        if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(e -> params.put(e.getKey(),
                    e.getValue().isNull() ? "" : e.getValue().asText()));
        } else if (!paramsNode.isMissingNode() && !paramsNode.isNull()) {
            throw new IllegalArgumentException("Transition parameters must be an object");
        }
        return new CanonicalTransition(
                transitionId,
                definitionId, definitionVersion, outgoingClipId, incomingClipId,
                mediaType, duration, alignment, temporalPolicy, params);
    }

    /** STRICT decode from a canonical JSON string. */
    public static CanonicalTransition fromCanonicalJson(String transitionId, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Transition canonical payload missing");
        }
        try {
            return fromCanonicalValue(transitionId, MAPPER.readTree(encoded));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Transition canonical payload", e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Transition requires non-blank '" + field + "'");
        }
        return v.asText();
    }

    /** Deterministic fingerprint — SHA-256 over canonical value; no delimiter
     *  collision is possible because the value is structured JSON. */
    public static String semanticFingerprint(CanonicalTransition t) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(t));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Transition canonical fingerprint failed", e);
        }
    }

    /** Canonical JSON as a string (lossless op payload). */
    public static String encode(CanonicalTransition t) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(t));
        } catch (Exception e) {
            throw new IllegalStateException("Transition canonical encode failed", e);
        }
    }

    public static boolean localSemanticsEquals(CanonicalTransition a, CanonicalTransition b) {
        return canonicalValue(a).equals(canonicalValue(b));
    }

    // ── Merge-transport projection (R5-A: snapshot is transport, not contract) ──

    /** Domain value → merge snapshot projection. */
    public static CanonicalTimelineTransitionSnapshot toSnapshotValue(CanonicalTransition t) {
        return new CanonicalTimelineTransitionSnapshot(
                t.transitionId() == null ? "" : t.transitionId(),
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

    /** Merge snapshot → domain value (transport back). */
    public static CanonicalTransition fromSnapshotValue(CanonicalTimelineTransitionSnapshot s) {
        return new CanonicalTransition(
                s.transitionId(),
                s.transitionDefinitionId(),
                s.transitionDefinitionVersion(),
                s.outgoingClipId(),
                s.incomingClipId(),
                s.mediaType(),
                s.duration(),
                s.alignment(),
                s.temporalPolicy(),
                s.parameters());
    }

    // ── Snapshot convenience (merge transport path; contract lives on the
    //    domain value above) ───────────────────────────────────────────────

    public static ObjectNode canonicalValue(CanonicalTimelineTransitionSnapshot t) {
        return canonicalValue(fromSnapshotValue(t));
    }

    public static String semanticFingerprint(CanonicalTimelineTransitionSnapshot t) {
        return semanticFingerprint(fromSnapshotValue(t));
    }

    public static String encode(CanonicalTimelineTransitionSnapshot t) {
        return encode(fromSnapshotValue(t));
    }

    public static boolean localSemanticsEquals(
            CanonicalTimelineTransitionSnapshot a, CanonicalTimelineTransitionSnapshot b) {
        return canonicalValue(fromSnapshotValue(a)).equals(canonicalValue(fromSnapshotValue(b)));
    }

    /** R4-A1: mapping between the canonical Transition domain value and the
     *  candidate representation — owned here so no adapter keeps a second
     *  Transition field grammar. */
    public static CanonicalTransition toCandidateValue(CanonicalTimelineTransitionSnapshot t) {
        return fromSnapshotValue(t);
    }
}
