package com.example.platform.timeline.canonical;

import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ROADMAP #19 — the ONE local TimedText semantic authority.
 *
 * Owns the complete canonical TextElement value (id + exact timing + StyledText
 * + frame + fallback policy + historical-frozen resolved font runs), the
 * deterministic semantic fingerprint, and the lossless encode/decode used by
 * the Timeline diff/patch/merge pipeline.
 *
 * No Java hashCode, no toString summaries, no delimiter grammars: canonical
 * semantics are typed JSON with deep key-sorted maps (map order non-semantic)
 * and preserved list order (runs are ordered semantics).
 */
public final class TimedTextCanonicalSemantics {

    private static final ObjectMapper MAPPER = canonicalMapper();

    private TimedTextCanonicalSemantics() {
    }

    private static ObjectMapper canonicalMapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);
        // ROADMAP #19 (D4): lossless decode of the font-text value types —
        // every TimedText value class has a single public constructor and the
        // build compiles with -parameters, so property-constructor detection
        // reconstructs them without per-type annotations.
        m.setConstructorDetector(com.fasterxml.jackson.databind.cfg.ConstructorDetector.USE_PROPERTIES_BASED);
        return m;
    }

    /** Deep deterministic sort: nested Maps key-sorted; Lists order-preserved. */
    private static Object deepSorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
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

    /**
     * The complete canonical TextElement value — every active authored
     * semantic field, typed and deep-deterministic. StyledText runs keep
     * their authored order (ordered semantics); run maps are deep-sorted.
     */
    public static Map<String, Object> canonicalValue(TextElement element) {
        StyledText st = element.styledText();
        TreeMap<String, Object> styled = new TreeMap<>();
        styled.put("content", st.content());
        styled.put("semanticRuns", st.semanticRuns().stream()
                .map(run -> runValue(run)).toList());
        styled.put("styleRuns", st.styleRuns().stream()
                .map(run -> runValue(run)).toList());
        styled.put("paragraphStyle", deepSorted(st.paragraphStyle()));

        TreeMap<String, Object> frame = new TreeMap<>();
        frame.put("widthConstraint", element.frame().widthConstraint());
        frame.put("heightConstraint", element.frame().heightConstraint());
        frame.put("horizontalAlignment", element.frame().horizontalAlignment());
        frame.put("verticalAlignment", element.frame().verticalAlignment());
        frame.put("wrapBehavior", element.frame().wrapBehavior());
        frame.put("overflowBehavior", element.frame().overflowBehavior());

        TreeMap<String, Object> node = new TreeMap<>();
        node.put("id", element.id().value());
        node.put("start", element.start());
        node.put("duration", element.duration());
        node.put("styledText", styled);
        node.put("frame", frame);
        node.put("fallbackPolicy", element.fallbackPolicy());
        node.put("resolvedFontRuns", deepSorted(element.resolvedFontRuns()));
        return node;
    }

    private static Map<String, Object> runValue(Object run) {
        TreeMap<String, Object> m = new TreeMap<>();
        for (var field : run.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object v = field.get(run);
                if (v != null) {
                    m.put(field.getName(), deepSorted(v));
                }
            } catch (IllegalAccessException ignored) {
                // field not readable — not part of canonical semantics
            }
        }
        return m;
    }

    /** Deterministic semantic fingerprint of one TextElement. */
    public static String semanticFingerprint(TextElement element) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(element));
        } catch (Exception ex) {
            throw new IllegalArgumentException("TextElement canonical value not serializable", ex);
        }
    }

    /** Lossless canonical encoding of the TextElement collection. */
    public static String encodeElements(List<TextElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(
                    elements.stream().map(TimedTextCanonicalSemantics::canonicalValue).toList());
        } catch (Exception ex) {
            throw new IllegalArgumentException("TextElement collection not serializable", ex);
        }
    }

    /** Lossless reconstruction of the TextElement collection from canonical encoding. */
    public static List<TextElement> decodeElements(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> nodes = MAPPER.readValue(encoded,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() { });
            List<TextElement> out = new ArrayList<>(nodes.size());
            for (Map<String, Object> node : nodes) {
                out.add(TextElementCodec.fromCanonical(node));
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid canonical TextElement payload: " + ex.getMessage(), ex);
        }
    }

    /** Decode one canonical TextElement from a JSON tree (codec mapper). */
    public static TextElement fromCanonicalNode(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return MAPPER.treeToValue(node, TextElement.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("TextElement canonical payload cannot be decoded", ex);
        }
    }

    /** Internal decode helper — reconstructs the authored value objects. */
    static final class TextElementCodec {
        static TextElement fromCanonical(Map<String, Object> node) {
            try {
                String json = MAPPER.writeValueAsString(node);
                return MAPPER.readValue(json, TextElement.class);
            } catch (Exception ex) {
                throw new IllegalArgumentException("TextElement canonical payload cannot be decoded", ex);
            }
        }
    }
}
