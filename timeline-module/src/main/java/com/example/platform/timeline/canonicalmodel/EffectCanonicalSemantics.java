package com.example.platform.timeline.canonicalmodel;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * FIFTH CORRECTION — the ONE local Effect semantic codec authority.
 *
 * <p>Owns deep, deterministic, type-preserving, collision-resistant canonical
 * encoding for Effect authored semantics (id + effectKey + parameters):
 *
 * <ul>
 *   <li>deep deterministic ordering: nested Maps are key-sorted recursively
 *       (TreeMap normalization before Jackson serialization)</li>
 *   <li>type preservation: integer 9 vs string "9", boolean true vs "true",
 *       null vs "" remain distinct (JSON typed encoding)</li>
 *   <li>collision resistance: values are JSON-escaped — no delimiter
 *       ambiguity (comma/equals/quotes/backslash/unprintable safe)</li>
 *   <li>List order is semantic (preserved verbatim)</li>
 * </ul>
 *
 * <p>CanonicalTimelineDiffCalculator and TimelinePatchApplier MUST NOT
 * reimplement Effect field grammar — both delegate here.
 */
public final class EffectCanonicalSemantics {

    private EffectCanonicalSemantics() {
    }

    /** Deep key-sorted copy: nested Maps normalized to TreeMap (recursive). */
    public static Object deepSorted(Object value) {
        if (value instanceof Map<?, ?> m) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), deepSorted(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(deepSorted(item));
            }
            return out;
        }
        return value;
    }

    /** Deterministic typed JSON encoding of a single value (sorted, escaped). */
    public static String encodeValue(Object value) {
        try {
            return InternalTimelineJson.mapper().writeValueAsString(deepSorted(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Effect parameter value is not JSON-serializable", ex);
        }
    }

    /**
     * Complete deterministic semantic fingerprint of one Effect:
     * id + effectKey + deep-canonical parameters.
     */
    public static String semanticFingerprint(TimelineClipEffect effect) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(effect.id() == null ? "" : effect.id()).append(';')
          .append("key=").append(effect.effectKey()).append(';')
          .append("params=").append(encodeValue(effect.parameters()));
        return sb.toString();
    }

    /** Canonical JSON encoding of an Effect list (lossless, typed). */
    public static String encodeEffects(List<TimelineClipEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> out = new ArrayList<>(effects.size());
        for (TimelineClipEffect e : effects) {
            TreeMap<String, Object> node = new TreeMap<>();
            node.put("id", e.id());
            node.put("effectKey", e.effectKey());
            node.put("parameters", e.parameters());
            out.add(node);
        }
        try {
            return InternalTimelineJson.mapper().writeValueAsString(out);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Effect list is not JSON-serializable", ex);
        }
    }

    /** Lossless typed decode of an Effect list (single authority). */
    public static List<TimelineClipEffect> decodeEffects(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        try {
            JsonNode arr = InternalTimelineJson.mapper().readTree(encoded);
            if (!arr.isArray()) {
                throw new IllegalArgumentException("Effect canonical encoding must be a JSON array");
            }
            List<TimelineClipEffect> out = new ArrayList<>();
            for (JsonNode node : arr) {
                String id = node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null;
                String effectKey = node.get("effectKey").asText();
                Map<String, Object> parameters = node.has("parameters") && node.get("parameters").isObject()
                        ? InternalTimelineJson.mapper().convertValue(node.get("parameters"),
                                new TypeReference<Map<String, Object>>() {})
                        : Map.of();
                out.add(new TimelineClipEffect(id, effectKey, parameters));
            }
            return List.copyOf(out);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Effect canonical encoding is malformed", ex);
        }
    }
}
