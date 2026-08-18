package com.example.platform.timeline.canonical;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.text.LanguageTag;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * ROADMAP #19 (CORRECTION 1) — the ONE local TimedText semantic authority.
 *
 * TIMEDTEXT_CANONICAL_SCHEMA_IS_EXPLICIT_NOT_REFLECTIVE_V1:
 * every active authored TimedText field is mapped EXPLICITLY through public
 * semantic accessors into a deterministic canonical node. No reflection
 * (getDeclaredFields/setAccessible), no generic bean-to-map, no field-name
 * leakage, no Jackson-driven canonical field discovery.
 *
 * Nullable authored fields (TextSemanticRun.language/script = UNSPECIFIED)
 * use an explicit JSON null — one frozen representation.
 *
 * List ordering is semantic (semanticRuns/styleRuns/fallback chains/feature
 * settings/variation coordinates preserve authored order); nested Maps are
 * key-sorted (non-semantic ordering).
 */
public final class TimedTextCanonicalSemantics {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);

    private TimedTextCanonicalSemantics() {
    }

    // ── canonical node construction (explicit, non-reflective) ──

    /** Canonical JSON node for one TextElement (complete authored semantics). */
    public static ObjectNode toCanonicalNode(TextElement element) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", element.id().value());
        node.set("start", rational(element.start()));
        node.set("duration", rational(element.duration()));
        node.set("styledText", styledText(element.styledText()));
        node.set("frame", frame(element.frame()));
        node.set("fallbackPolicy", fallbackPolicy(element.fallbackPolicy()));
        ArrayNode runs = MAPPER.createArrayNode();
        for (ResolvedFontRun run : element.resolvedFontRuns()) {
            runs.add(resolvedFontRun(run));
        }
        node.set("resolvedFontRuns", runs);
        return node;
    }

    /** Explicit canonical map form (stable, insertion-order independent). */
    public static TreeMap<String, Object> canonicalValue(TextElement element) {
        try {
            return MAPPER.convertValue(toCanonicalNode(element), TreeMap.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("TextElement canonical value not serializable", ex);
        }
    }

    private static ObjectNode styledText(StyledText st) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("content", st.content().value());
        ArrayNode semantic = MAPPER.createArrayNode();
        for (TextSemanticRun run : st.semanticRuns()) {
            ObjectNode rn = MAPPER.createObjectNode();
            rn.set("range", range(run.range()));
            rn.put("language", run.language() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                    : com.fasterxml.jackson.databind.node.TextNode.valueOf(run.language().value()));
            rn.put("script", run.script() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                    : com.fasterxml.jackson.databind.node.TextNode.valueOf(run.script().value()));
            rn.put("directionOverride", run.directionOverride() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                    : com.fasterxml.jackson.databind.node.TextNode.valueOf(run.directionOverride().name()));
            semantic.add(rn);
        }
        node.set("semanticRuns", semantic);
        ArrayNode style = MAPPER.createArrayNode();
        for (TextStyleRun run : st.styleRuns()) {
            ObjectNode rn = MAPPER.createObjectNode();
            rn.set("range", range(run.range()));
            rn.set("style", style(run.style()));
            style.add(rn);
        }
        node.set("styleRuns", style);
        node.set("paragraphStyle", paragraphStyle(st.paragraphStyle()));
        return node;
    }

    private static ObjectNode range(TextRange r) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("start", r.start());
        n.put("end", r.end());
        return n;
    }

    private static ObjectNode style(TextStyle s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.set("fontSelection", fontSelection(s.fontSelection()));
        ObjectNode fs = MAPPER.createObjectNode();
        fs.set("value", rational(s.fontSize().value()));
        n.set("fontSize", fs);
        n.set("tracking", rational(s.tracking()));
        n.set("features", features(s.features()));
        return n;
    }

    private static ObjectNode fontSelection(FontSelectionIntent i) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode prefs = MAPPER.createArrayNode();
        for (FontFamilyName f : i.familyPreferences()) {
            prefs.add(f.value());
        }
        n.set("familyPreferences", prefs);
        n.put("weight", i.weight() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(i.weight().name()));
        n.put("stretch", i.stretch() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(i.stretch().name()));
        n.put("slant", i.slant() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(i.slant().name()));
        if (i.opticalSizing() != null) {
            ObjectNode os = MAPPER.createObjectNode();
            os.put("kind", i.opticalSizing().kind().name());
            os.set("explicitCoordinate",
                    i.opticalSizing().explicitCoordinate() == null ? null : rational(i.opticalSizing().explicitCoordinate()));
            n.set("opticalSizing", os);
        } else {
            n.set("opticalSizing", null);
        }
        ArrayNode axes = MAPPER.createArrayNode();
        for (VariationCoordinate v : i.explicitAxisOverrides()) {
            axes.add(variationCoordinate(v));
        }
        n.set("explicitAxisOverrides", axes);
        return n;
    }

    private static ObjectNode variationCoordinate(VariationCoordinate v) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("axis", v.axis().value());
        n.set("coordinate", rational(v.coordinate()));
        return n;
    }

    private static ObjectNode features(OpenTypeFeatureIntent f) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode settings = MAPPER.createArrayNode();
        for (OpenTypeFeatureIntent.OpenTypeFeatureSetting s : f.settings()) {
            ObjectNode sn = MAPPER.createObjectNode();
            sn.put("tag", s.tag().value());
            sn.put("state", s.state().name());
            settings.add(sn);
        }
        n.set("settings", settings);
        return n;
    }

    private static ObjectNode paragraphStyle(ParagraphStyle p) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("alignment", p.alignment() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(p.alignment().name()));
        n.put("justification", p.justification() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(p.justification().name()));
        if (p.lineHeight() != null) {
            ObjectNode lh = MAPPER.createObjectNode();
            lh.put("form", p.lineHeight().form().name());
            lh.set("value", p.lineHeight().value() == null ? null : rational(p.lineHeight().value()));
            n.set("lineHeight", lh);
        } else {
            n.set("lineHeight", null);
        }
        n.put("wrapPolicy", p.wrapPolicy() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(p.wrapPolicy().name()));
        n.put("baseDirection", p.baseDirection() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(p.baseDirection().name()));
        n.put("lineBreakPolicy", p.lineBreakPolicy() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(p.lineBreakPolicy().name()));
        return n;
    }

    private static ObjectNode frame(TextFrame f) {
        ObjectNode n = MAPPER.createObjectNode();
        n.set("widthConstraint", f.widthConstraint() == null ? null : rational(f.widthConstraint()));
        n.set("heightConstraint", f.heightConstraint() == null ? null : rational(f.heightConstraint()));
        n.put("horizontalAlignment", f.horizontalAlignment() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(f.horizontalAlignment().name()));
        n.put("verticalAlignment", f.verticalAlignment() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(f.verticalAlignment().name()));
        n.put("wrapBehavior", f.wrapBehavior() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(f.wrapBehavior().name()));
        n.put("overflowBehavior", f.overflowBehavior() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(f.overflowBehavior().name()));
        return n;
    }

    private static ObjectNode fallbackPolicy(FontFallbackPolicy p) {
        ObjectNode n = MAPPER.createObjectNode();
        ArrayNode chain = MAPPER.createArrayNode();
        for (FontFamilyName f : p.defaultChain()) {
            chain.add(f.value());
        }
        n.set("defaultChain", chain);
        ArrayNode scriptOverrides = MAPPER.createArrayNode();
        for (FontFallbackPolicy.ScriptOverride o : p.scriptOverrides()) {
            ObjectNode on = MAPPER.createObjectNode();
            on.put("script", o.script() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(o.script().value()));
            ArrayNode ochain = MAPPER.createArrayNode();
            for (FontFamilyName f : o.chain()) {
                ochain.add(f.value());
            }
            on.set("chain", ochain);
            scriptOverrides.add(on);
        }
        n.set("scriptOverrides", scriptOverrides);
        ArrayNode langOverrides = MAPPER.createArrayNode();
        for (FontFallbackPolicy.LanguageOverride o : p.languageOverrides()) {
            ObjectNode on = MAPPER.createObjectNode();
            on.put("language", o.language() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(o.language().value()));
            ArrayNode ochain = MAPPER.createArrayNode();
            for (FontFamilyName f : o.chain()) {
                ochain.add(f.value());
            }
            on.set("chain", ochain);
            langOverrides.add(on);
        }
        n.set("languageOverrides", langOverrides);
        ArrayNode emoji = MAPPER.createArrayNode();
        for (FontFamilyName f : p.emojiChain()) {
            emoji.add(f.value());
        }
        n.set("emojiChain", emoji);
        return n;
    }

    private static ObjectNode resolvedFontRun(ResolvedFontRun run) {
        ObjectNode n = MAPPER.createObjectNode();
        n.set("range", range(run.range()));
        n.set("font", resolvedFontInstance(run.font()));
        return n;
    }

    private static ObjectNode resolvedFontInstance(ResolvedFontInstance f) {
        ObjectNode n = MAPPER.createObjectNode();
        n.set("executionReference", executionReference(f.executionReference()));
        ArrayNode coords = MAPPER.createArrayNode();
        for (VariationCoordinate v : f.variationCoordinates()) {
            coords.add(variationCoordinate(v));
        }
        n.set("variationCoordinates", coords);
        return n;
    }

    private static ObjectNode executionReference(ValidatedFontExecutionReference r) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("sourceFontContentDigest", r.sourceFontContentDigest().sha256Hex());
        n.put("validatedExecutionContentDigest", r.validatedExecutionContentDigest().sha256Hex());
        n.put("securityState", r.securityState() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(r.securityState().name()));
        n.put("format", r.format() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : com.fasterxml.jackson.databind.node.TextNode.valueOf(r.format().name()));
        n.set("faceIndex", r.faceIndex() == null ? null : MAPPER.createObjectNode().put("value", r.faceIndex().value()));
        return n;
    }

    private static ObjectNode rational(FontRational r) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("numerator", r.numerator().toString());
        n.put("denominator", r.denominator().toString());
        return n;
    }

    // ── fingerprints / encoding (all from the explicit canonical value) ──

    /** Deterministic semantic fingerprint of one TextElement. */
    public static String semanticFingerprint(TextElement element) {
        try {
            return MAPPER.writeValueAsString(toCanonicalNode(element));
        } catch (Exception ex) {
            throw new IllegalArgumentException("TextElement canonical value not serializable", ex);
        }
    }

    /** Lossless canonical encoding of the TextElement collection. */
    public static String encodeElements(List<TextElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return "[]";
        }
        ArrayNode arr = MAPPER.createArrayNode();
        for (TextElement e : elements) {
            arr.add(toCanonicalNode(e));
        }
        try {
            return MAPPER.writeValueAsString(arr);
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
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(encoded);
            List<TextElement> out = new ArrayList<>(root.size());
            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                out.add(MAPPER.treeToValue(node, TextElement.class));
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid canonical TextElement payload", ex);
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
}
