package com.example.platform.fonttext;

import com.example.platform.fonttext.diagnostics.FontTextDiagnostic;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resolution.OpticalSizingResolverPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resolution.TechnicalFontResolver;
import com.example.platform.fonttext.resolution.ValidatedFontCatalogSnapshot;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.fonttext.text.LanguageTag;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.RangeDirectionOverride;
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
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ROADMAP_19: pure-domain invariants (C1-C48 satisfaction probes). */
class FontTextFoundationTest {

    // ---------- C9/C10: authored Unicode preserved, no normalization ----------
    @Test
    void authoredUnicodePreservedNoNormalization() {
        TextContent composed = new TextContent("\u00e9");            // U+00E9
        TextContent decomposed = new TextContent("e\u0301");         // U+0065 U+0301
        assertNotEquals(composed, decomposed, "U+00E9 != U+0065 U+0301 (no silent NFC)");
        assertEquals(1, composed.scalarCount());
        assertEquals(2, decomposed.scalarCount());
    }

    @Test
    void malformedUnicodeFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new TextContent("a\uD800b"));
        assertThrows(IllegalArgumentException.class, () -> new TextContent("\uDC00"));
    }

    // ---------- C11: scalar ranges, UTF-16 never canonical ----------
    @Test
    void scalarRangeAstralEmoji() {
        TextContent t = new TextContent("A\uD83D\uDE00B"); // A + emoji + B
        assertEquals(3, t.scalarCount());
        assertEquals(3, t.utf16OffsetForScalar(2));
        TextRange range = new TextRange(1, 2); // exactly the emoji
        range.withBound(t.scalarCount());
        assertTrue(range.overlaps(new TextRange(0, 2)));
        assertFalse(range.overlaps(new TextRange(2, 3)));
    }

    // ---------- C13/C14: language/script/direction distinct ----------
    @Test
    void languageScriptDirectionDistinct() {
        assertNotEquals(LanguageTag.of("ar"), ScriptTag.ARABIC);
        assertNotEquals(ParagraphBaseDirection.RTL, RangeDirectionOverride.RTL);
        assertThrows(IllegalArgumentException.class, () -> new LanguageTag("en_US_"));
        assertThrows(IllegalArgumentException.class, () -> new ScriptTag("Latin"));
    }

    // ---------- C43/R3: line height sole ParagraphStyle authority ----------
    @Test
    void lineHeightOnlyInParagraphStyle() {
        assertThrows(NoSuchFieldException.class, () -> TextStyle.class.getDeclaredField("lineHeight"));
        assertThrows(NoSuchFieldException.class, () -> TextStyle.class.getDeclaredField("fill"));
        ParagraphStyle p = paragraphStyle(LineHeight.ratio(FontRational.of(1, 2)));
        assertEquals(LineHeight.ratio(FontRational.of(1, 2)), p.lineHeight());
    }

    // ---------- C44/R4: selection fields only in FontSelectionIntent ----------
    @Test
    void selectionFieldsOnlyInIntent() {
        assertThrows(NoSuchFieldException.class, () -> TextStyle.class.getDeclaredField("weight"));
        assertThrows(NoSuchFieldException.class, () -> TextStyle.class.getDeclaredField("slant"));
        assertThrows(NoSuchFieldException.class, () -> TextStyle.class.getDeclaredField("explicitAxisOverrides"));
    }

    // ---------- C26/C27: variable axes exact + sorted ----------
    @Test
    void variationAxesExactAndOrdered() {
        VariationCoordinate w = new VariationCoordinate(VariationAxisTag.WEIGHT, FontRational.of(650, 1));
        VariationCoordinate s = new VariationCoordinate(VariationAxisTag.SLANT, FontRational.of(1, 10));
        List<VariationCoordinate> sorted = new java.util.ArrayList<>(List.of(s, w));
        sorted.sort(null);
        assertEquals(List.of(s, w), sorted, "deterministic tag order");
        assertThrows(IllegalArgumentException.class, () -> new VariationAxisTag("wghtx"));
        assertThrows(IllegalArgumentException.class, () -> new VariationCoordinate(VariationAxisTag.WEIGHT, FontRational.of(0, 0)));
    }

    // ---------- C28: AUTO opsz fails closed without policy ----------
    @Test
    void autoOpticalSizingFailsClosedWithoutPolicy() {
        ValidatedFontCatalogSnapshot catalog = catalogWith("Inter");
        TechnicalFontResolver resolver = new TechnicalFontResolver(runtime());
        FontSelectionIntent intent = new FontSelectionIntent(
                List.of(new FontFamilyName("Inter")),
                FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.auto(), List.of());
        TextContent content = new TextContent("Hello");
        TechnicalFontResolver.Result result = resolver.resolve(content, intent, fallback(), catalog, null);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == FontTextDiagnostic.Code.AUTO_OPTICAL_SIZING_UNRESOLVED),
                "AUTO must fail closed without resolver policy");
        assertTrue(result.runs().isEmpty());
    }

    // ---------- C40/C42: technical resolution only, no Rights ----------
    @Test
    void resolverSelectsExactValidatedFontDeterministically() {
        ValidatedFontCatalogSnapshot catalog = catalogWith("Inter");
        TechnicalFontResolver resolver = new TechnicalFontResolver(runtime());
        FontSelectionIntent intent = new FontSelectionIntent(
                List.of(new FontFamilyName("Inter")),
                FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of());
        TechnicalFontResolver.Result result = resolver.resolve(
                new TextContent("Hello world"), intent, fallback(), catalog,
                size -> FontRational.whole(12));
        assertEquals(1, result.runs().size());
        ResolvedFontRun run = result.runs().get(0);
        assertEquals(FontContentDigest.ofText("inter-font-v1"), run.font().validatedDigest());
        assertEquals(new FaceIndex(0), run.font().faceIndex());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void missingGlyphProducesExplicitDiagnostic() {
        ValidatedFontCatalogSnapshot catalog = catalogWith("Inter");
        TechnicalFontResolver resolver = new TechnicalFontResolver(runtime());
        FontSelectionIntent intent = new FontSelectionIntent(
                List.of(new FontFamilyName("Inter")),
                FontSelectionIntent.WeightIntent.NORMAL, FontSelectionIntent.StretchIntent.NORMAL,
                FontSelectionIntent.SlantIntent.NORMAL, OpticalSizingIntent.disabled(), List.of());
        TechnicalFontResolver.Result result = resolver.resolve(
                new TextContent("Hello \uD83D\uDE00"), intent, fallback(), catalog,
                size -> FontRational.whole(12));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == FontTextDiagnostic.Code.MISSING_GLYPH),
                "missing glyph must be explicit, never silent tofu");
    }

    // ---------- C8: raw font never a validated execution reference ----------
    @Test
    void rawFontCannotEnterResolver() {
        assertThrows(IllegalArgumentException.class, () -> new ValidatedFontExecutionReference(
                FontContentDigest.ofText("raw"), FontContentDigest.ofText("raw"),
                FontSecurityState.RAW, FontFormat.TRUETYPE, new FaceIndex(0)));
    }

    private static TechnicalFontResolver.RuntimeCapabilityView runtime() {
        return new TechnicalFontResolver.RuntimeCapabilityView() {
            @Override public boolean supportsFormat(String formatName) { return true; }
            @Override public boolean supportsColorTechnology(String technology) { return true; }
            @Override public boolean supportsVariationAxes() { return true; }
            @Override public boolean supportsOpticalSizing() { return true; }
        };
    }

    private static com.example.platform.fonttext.resolution.FontFallbackPolicy fallback() {
        return new com.example.platform.fonttext.resolution.FontFallbackPolicy(
                List.of(new FontFamilyName("Arial")), List.of(), List.of(), List.of());
    }

    private static ValidatedFontCatalogSnapshot catalogWith(String family) {
        FontContentDigest digest = FontContentDigest.ofText(family.toLowerCase() + "-font-v1");
        ValidatedFontExecutionReference ref = new ValidatedFontExecutionReference(
                digest, digest, FontSecurityState.VALIDATED_EXECUTION_FONT,
                FontFormat.TRUETYPE, new FaceIndex(0));
        FontFaceManifest manifest = new FontFaceManifest(
                digest, new FaceIndex(0), FontFormat.TRUETYPE, family, "Regular",
                400, 100, "normal", 1000, fullAsciiCoverage(),
                Set.of(ScriptTag.LATIN), true, false, true,
                List.of(), List.of(), false, false, List.of(),
                "VALIDATED", "CONFORMANCE_EVALUATED");
        return new ValidatedFontCatalogSnapshot(List.of(
                new ValidatedFontCatalogSnapshot.Entry(ref, manifest)));
    }

    private static Set<Integer> fullAsciiCoverage() {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        for (int i = 0x20; i <= 0x7E; i++) s.add(i);
        return s;
    }

    private static ParagraphStyle paragraphStyle(LineHeight lineHeight) {
        return new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                lineHeight, ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                ParagraphStyle.LineBreakPolicy.STANDARD);
    }
}
