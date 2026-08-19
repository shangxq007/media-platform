package com.example.platform.timeline.semantics.automation;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationKeyframe;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3; R5-A: authority
 * re-homed on the canonical DOMAIN value {@link CanonicalAutomationCurve}):
 * the single Automation-local semantic authority.
 *
 * <p>The canonical semantic contract is defined over the domain value
 * {@link CanonicalAutomationCurve} / {@link CanonicalAutomationKeyframe}
 * (canonicalmodel) — NOT over the central diff/merge snapshot projection
 * {@link CanonicalTimelineAutomationSnapshot}. The snapshot is only merge
 * transport: {@link #toSnapshotValue} / {@link #fromSnapshotValue} project
 * to/from it; it never defines the Automation canonical semantic contract.
 *
 * <p>Owns ONLY Automation-local semantics: canonical authored curve value,
 * exact MediaTime keyframe representation, semantic equality, deterministic
 * fingerprint, lossless encode / STRICT decode / reconstruction.
 *
 * <p>STRICT decode (R5-A): a canonical authored Automation payload requires
 * targetEntityId, parameterPath, valueType, extrapolation and the keyframes
 * structure; each keyframe requires keyframeId, timeTicks, timeTimeScale,
 * value and interpolation. Missing or malformed REQUIRED fields FAIL CLOSED —
 * no synthesized valueType/extrapolation/interpolation/0.0 values, no
 * generated kf_N keyframe ids, no implicit timeScale=1. An explicitly empty
 * keyframes array remains valid (zero-keyframe authored curve).
 *
 * <p>Timeline keeps aggregate orchestration: automation identity/path, target
 * existence, target×deletion conflicts, Effect-instance cross-object rules,
 * aggregate three-way orchestration.
 *
 * <p>No delimiter grammar — authored strings/paths/values survive untouched.
 */
public final class AutomationCanonicalSemantics {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private AutomationCanonicalSemantics() {}

    // ── Canonical value over the DOMAIN value (R5-A authority) ────────────

    public static ObjectNode canonicalValue(CanonicalAutomationCurve c) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("targetEntityId", c.targetEntityId());
        node.put("parameterPath", c.parameterPath());
        node.put("valueType", c.valueType());
        node.put("extrapolation", c.extrapolation());
        ArrayNode kfs = node.putArray("keyframes");
        for (CanonicalAutomationKeyframe k : c.keyframes()) {
            ObjectNode kf = kfs.addObject();
            kf.put("keyframeId", k.keyframeId());
            kf.put("timeTicks", k.time().ticks());
            kf.put("timeTimeScale", k.time().timeScale());
            kf.put("value", k.value());
            kf.put("interpolation", k.interpolation());
        }
        return node;
    }

    /** STRICT lossless reconstruction from the canonical value (R5-A). Every
     *  REQUIRED authored field must be present and valid — missing/malformed
     *  fields FAIL CLOSED (IllegalArgumentException). No synthesized
     *  valueType/extrapolation/interpolation/0.0, no generated kf_N ids, no
     *  implicit timeScale=1. An explicitly empty keyframes array is a valid
     *  zero-keyframe authored curve; a MISSING keyframes field is not. */
    public static CanonicalAutomationCurve fromCanonicalValue(String automationId, JsonNode node) {
        String targetEntityId = requiredText(node, "targetEntityId");
        String parameterPath = requiredText(node, "parameterPath");
        String valueType = requiredText(node, "valueType");
        String extrapolation = requiredText(node, "extrapolation");
        JsonNode kfNode = node.path("keyframes");
        if (!kfNode.isArray()) {
            throw new IllegalArgumentException(
                    "Automation requires a keyframes array (may be empty)");
        }
        List<CanonicalAutomationKeyframe> kfs = new ArrayList<>();
        for (JsonNode k : kfNode) {
            String keyframeId = requiredText(k, "keyframeId");
            if (!k.has("timeTicks") || !k.has("timeTimeScale")) {
                throw new IllegalArgumentException(
                        "Automation keyframe '" + keyframeId
                                + "' requires timeTicks and timeTimeScale");
            }
            // F2 (post-Round-5): integral JSON nodes REQUIRED — no Jackson
            // coercion of strings/booleans/objects/arrays into numbers.
            JsonNode ticksNode = k.path("timeTicks");
            JsonNode scaleNode = k.path("timeTimeScale");
            if (!ticksNode.isIntegralNumber() || !scaleNode.isIntegralNumber()) {
                throw new IllegalArgumentException(
                        "Automation keyframe '" + keyframeId
                                + "' timeTicks/timeTimeScale must be integral JSON numbers");
            }
            long timeTicks = ticksNode.asLong();
            long timeTimeScale = scaleNode.asLong();
            if (timeTimeScale <= 0) {
                throw new IllegalArgumentException(
                        "Automation keyframe '" + keyframeId
                                + "' requires positive timeTimeScale");
            }
            MediaTime time = MediaTime.ofTicks(timeTicks, timeTimeScale);
            if (!k.has("value") || !k.path("value").isNumber()) {
                throw new IllegalArgumentException(
                        "Automation keyframe '" + keyframeId + "' requires a numeric value");
            }
            double value = k.path("value").asDouble();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Automation keyframe '" + keyframeId + "' requires a finite value");
            }
            String interpolation = requiredText(k, "interpolation");
            kfs.add(new CanonicalAutomationKeyframe(
                    keyframeId, time, value, interpolation));
        }
        return new CanonicalAutomationCurve(
                automationId, targetEntityId, parameterPath, valueType, extrapolation, kfs);
    }

    /** STRICT decode from a canonical JSON string. */
    public static CanonicalAutomationCurve fromCanonicalJson(String automationId, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Automation canonical payload missing");
        }
        try {
            return fromCanonicalValue(automationId, MAPPER.readTree(encoded));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Automation canonical payload", e);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Automation requires non-blank '" + field + "'");
        }
        return v.asText();
    }

    public static String semanticFingerprint(CanonicalAutomationCurve c) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(c));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Automation canonical fingerprint failed", e);
        }
    }

    /** Lossless op payload for the automation (keyframes included). */
    public static String encode(CanonicalAutomationCurve c) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(c));
        } catch (Exception e) {
            throw new IllegalStateException("Automation canonical encode failed", e);
        }
    }

    public static boolean localSemanticsEquals(
            CanonicalAutomationCurve a, CanonicalAutomationCurve b) {
        return canonicalValue(a).equals(canonicalValue(b));
    }

    // ── Merge-transport projection (R5-A: snapshot is transport, not contract) ──

    /** Domain value → merge snapshot projection. */
    public static CanonicalTimelineAutomationSnapshot toSnapshotValue(CanonicalAutomationCurve c) {
        List<CanonicalTimelineAutomationKeyframe> kfs = c.keyframes().stream()
                .map(k -> new CanonicalTimelineAutomationKeyframe(
                        k.keyframeId(), k.time(), k.value(), k.interpolation()))
                .toList();
        return new CanonicalTimelineAutomationSnapshot(
                c.automationId(), c.targetEntityId(), c.parameterPath(),
                c.valueType(), c.extrapolation(), kfs);
    }

    /** Merge snapshot → domain value (transport back). */
    public static CanonicalAutomationCurve fromSnapshotValue(CanonicalTimelineAutomationSnapshot s) {
        return new CanonicalAutomationCurve(
                s.automationId(), s.targetEntityId(), s.parameterPath(),
                s.valueType(), s.extrapolation(),
                s.keyframes().stream()
                        .map(k -> new CanonicalAutomationKeyframe(
                                k.keyframeId(), k.time(), k.value(), k.interpolation()))
                        .toList());
    }

    // ── Snapshot convenience (merge transport path; contract lives on the
    //    domain value above) ───────────────────────────────────────────────

    public static ObjectNode canonicalValue(CanonicalTimelineAutomationSnapshot c) {
        return canonicalValue(fromSnapshotValue(c));
    }

    public static String semanticFingerprint(CanonicalTimelineAutomationSnapshot c) {
        return semanticFingerprint(fromSnapshotValue(c));
    }

    /** Lossless op payload for the automation (keyframes included). */
    public static String encode(CanonicalTimelineAutomationSnapshot c) {
        return encode(fromSnapshotValue(c));
    }

    public static boolean localSemanticsEquals(
            CanonicalTimelineAutomationSnapshot a, CanonicalTimelineAutomationSnapshot b) {
        return canonicalValue(fromSnapshotValue(a)).equals(canonicalValue(fromSnapshotValue(b)));
    }

    /** R4-A2: mapping between the canonical Automation domain value and the
     *  candidate representation — owned here so no adapter keeps a second
     *  Automation field grammar. */
    public static CanonicalAutomationCurve toCandidateValue(CanonicalTimelineAutomationSnapshot c) {
        return fromSnapshotValue(c);
    }
}
