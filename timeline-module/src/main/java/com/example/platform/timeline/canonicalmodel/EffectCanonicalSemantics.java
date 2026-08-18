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
     * SIXTH CORRECTION (S1/S2) — THE ONE complete canonical Effect semantic
     * value: id + effectKey + deep-canonical parameters as a typed structure.
     * No manual delimiter framing; JSON escaping handles arbitrary legal
     * authored string contents; field knowledge lives here ONCE.
     */
    public static Map<String, Object> canonicalEffectValue(TimelineClipEffect effect) {
        TreeMap<String, Object> node = new TreeMap<>();
        node.put("id", effect.id());
        node.put("effectKey", effect.effectKey());
        node.put("parameters", deepSorted(effect.parameters()));
        return node;
    }

    /**
     * SIXTH CORRECTION (S1) — complete deterministic semantic fingerprint of
     * one Effect, derived from the complete canonical Effect value. Manual
     * "id=...;key=..." delimiter envelope REMOVED (collision-free: id/effectKey
     * are typed JSON fields; arbitrary ";", "=", ",", quotes, backslash are
     * escaped by JSON).
     */
    public static String semanticFingerprint(TimelineClipEffect effect) {
        try {
            return InternalTimelineJson.mapper().writeValueAsString(canonicalEffectValue(effect));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Effect canonical value is not JSON-serializable", ex);
        }
    }

    /** Canonical JSON encoding of an Effect list (lossless, typed). */
    public static String encodeEffects(List<TimelineClipEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "[]";
        }
        // SIXTH CORRECTION (S2): every element is the SAME canonical Effect
        // value used by semanticFingerprint (deepSorted parameters) — one
        // representation, one field-knowledge location.
        List<Map<String, Object>> out = new ArrayList<>(effects.size());
        for (TimelineClipEffect e : effects) {
            out.add(canonicalEffectValue(e));
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
