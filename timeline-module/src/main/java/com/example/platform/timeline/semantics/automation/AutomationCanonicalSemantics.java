package com.example.platform.timeline.semantics.automation;

import com.example.platform.shared.time.MediaTime;
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
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): the single
 * Automation-local semantic authority.
 *
 * <p>Owns ONLY Automation-local semantics: canonical authored curve value,
 * exact MediaTime keyframe representation, semantic equality, deterministic
 * fingerprint, lossless encode/decode/reconstruction.
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

    public static ObjectNode canonicalValue(CanonicalTimelineAutomationSnapshot c) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("targetEntityId", c.targetEntityId() == null ? "" : c.targetEntityId());
        node.put("parameterPath", c.parameterPath() == null ? "" : c.parameterPath());
        node.put("valueType", c.valueType() == null ? "" : c.valueType());
        node.put("extrapolation", c.extrapolation() == null ? "" : c.extrapolation());
        ArrayNode kfs = node.putArray("keyframes");
        for (CanonicalTimelineAutomationKeyframe k : c.keyframes()) {
            ObjectNode kf = kfs.addObject();
            kf.put("keyframeId", k.keyframeId() == null ? "" : k.keyframeId());
            if (k.time() != null) {
                kf.put("timeTicks", k.time().ticks());
                kf.put("timeTimeScale", k.time().timeScale());
            }
            kf.put("value", k.value());
            kf.put("interpolation", k.interpolation() == null ? "" : k.interpolation());
        }
        return node;
    }

    public static CanonicalTimelineAutomationSnapshot fromCanonicalValue(String automationId, JsonNode node) {
        List<CanonicalTimelineAutomationKeyframe> kfs = new ArrayList<>();
        JsonNode kfNode = node.path("keyframes");
        if (kfNode.isArray()) {
            for (JsonNode k : kfNode) {
                MediaTime time = null;
                if (k.has("timeTicks")) {
                    time = MediaTime.ofTicks(k.path("timeTicks").asLong(), k.path("timeTimeScale").asLong(1));
                }
                kfs.add(new CanonicalTimelineAutomationKeyframe(
                        k.path("keyframeId").asText(""),
                        time,
                        k.path("value").asDouble(0.0),
                        k.path("interpolation").asText("")));
            }
        }
        return new CanonicalTimelineAutomationSnapshot(
                automationId,
                node.path("targetEntityId").asText(""),
                node.path("parameterPath").asText(""),
                node.path("valueType").asText(""),
                node.path("extrapolation").asText(""),
                kfs);
    }

    public static String semanticFingerprint(CanonicalTimelineAutomationSnapshot c) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(c));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Automation canonical fingerprint failed", e);
        }
    }

    /** Lossless op payload for the automation (keyframes included). */
    public static String encode(CanonicalTimelineAutomationSnapshot c) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(c));
        } catch (Exception e) {
            throw new IllegalStateException("Automation canonical encode failed", e);
        }
    }

    public static boolean localSemanticsEquals(
            CanonicalTimelineAutomationSnapshot a, CanonicalTimelineAutomationSnapshot b) {
        return canonicalValue(a).equals(canonicalValue(b));
    }
}
