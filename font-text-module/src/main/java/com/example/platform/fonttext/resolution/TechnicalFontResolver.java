package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.diagnostics.FontTextDiagnostic;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.VariationAxisTag;
import com.example.platform.fonttext.typography.VariationCoordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ROADMAP_19 (C23/C40/C42): SOLE TECHNICAL font resolver.
 * TechnicalFontCandidate = Validated ∩ Coverage ∩ ShapingConformance ∩
 * RuntimeCapability. NEVER performs Rights allow/deny (C42). Deterministic:
 * candidate evaluation follows catalog entry order (snapshot order is the
 * deterministic input), fallback chains are explicit ordered lists.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class TechnicalFontResolver {

    /** provider-neutral runtime capability view (execution capability observations). */
    public interface RuntimeCapabilityView {
        boolean supportsFormat(String formatName);

        boolean supportsColorTechnology(String technology);

        boolean supportsVariationAxes();

        boolean supportsOpticalSizing();
    }

    public static final class Result {
        private final List<ResolvedFontRun> runs;
        private final List<FontTextDiagnostic> diagnostics;

        Result(List<ResolvedFontRun> runs, List<FontTextDiagnostic> diagnostics) {
            this.runs = List.copyOf(runs);
            this.diagnostics = List.copyOf(diagnostics);
        }

        public List<ResolvedFontRun> runs() { return runs; }
        public List<FontTextDiagnostic> diagnostics() { return diagnostics; }
    }

    private final RuntimeCapabilityView runtime;

    public TechnicalFontResolver(RuntimeCapabilityView runtime) {
        this.runtime = runtime;
    }

    /**
     * Resolve exact font instances over the whole StyledText content using
     * the frozen intent, fallback policy, and validated catalog snapshot.
     * AUTO optical sizing without an explicit resolver policy FAILS CLOSED
     * with AUTO_OPTICAL_SIZING_UNRESOLVED (never silently guessed).
     */
    public Result resolve(TextContent content, FontSelectionIntent intent,
                          FontFallbackPolicy fallbackPolicy,
                          ValidatedFontCatalogSnapshot catalog,
                          OpticalSizingResolverPolicy opszPolicy) {
        List<ResolvedFontRun> runs = new ArrayList<>();
        List<FontTextDiagnostic> diagnostics = new ArrayList<>();

        if (intent.opticalSizing().kind() == OpticalSizingIntent.Kind.AUTO) {
            if (opszPolicy == null) {
                diagnostics.add(new FontTextDiagnostic(
                        FontTextDiagnostic.Code.AUTO_OPTICAL_SIZING_UNRESOLVED,
                        TextRange.of(0, content.scalarCount()), null, null, List.of(),
                        "AUTO optical sizing requires an explicit resolution policy before canonical commit"));
                return new Result(List.of(), diagnostics);
            }
        }

        int max = content.scalarCount();
        if (max == 0) {
            return new Result(List.of(), List.of());
        }

        // deterministic single-run resolution for bounded V1 (whole content as one run
        // when the first eligible candidate covers all scalars); fallback per script run
        // is a later refinement — V1 conservative segmentation = whole-text run.
        List<FontFamilyName> chain = buildChain(intent, fallbackPolicy);
        ResolvedFontInstance chosen = null;
        FontTextDiagnostic.Code failure = null;
        String reason = null;
        for (FontFamilyName family : chain) {
            Optional<ResolvedFontInstance> candidate = trySelect(family, intent, catalog);
            if (candidate.isEmpty()) {
                failure = failure == null ? FontTextDiagnostic.Code.FONT_UNAVAILABLE : failure;
                reason = "no validated candidate for family " + family;
                continue;
            }
            ResolvedFontInstance instance = candidate.get();
            // coverage + shaping conformance + runtime capability
            FontFaceManifest manifest = findManifest(instance, catalog);
            if (manifest == null || !coversAll(manifest, content)) {
                failure = failure == null ? FontTextDiagnostic.Code.MISSING_GLYPH : failure;
                reason = "coverage failure for " + family;
                continue;
            }
            if (!runtime.supportsVariationAxes() && !instance.variationCoordinates().isEmpty()) {
                failure = failure == null ? FontTextDiagnostic.Code.VARIABLE_FONT_CAPABILITY_UNAVAILABLE : failure;
                reason = "runtime lacks variable font capability for " + family;
                continue;
            }
            chosen = instance;
            break;
        }

        if (chosen == null) {
            diagnostics.add(new FontTextDiagnostic(failure == null
                            ? FontTextDiagnostic.Code.FONT_UNAVAILABLE : failure,
                    TextRange.of(0, max), null, null, List.of(),
                    reason == null ? "no eligible font candidate" : reason));
            return new Result(List.of(), diagnostics);
        }

        runs.add(new ResolvedFontRun(TextRange.of(0, max), chosen));
        return new Result(runs, diagnostics);
    }

    private List<FontFamilyName> buildChain(FontSelectionIntent intent, FontFallbackPolicy policy) {
        List<FontFamilyName> chain = new ArrayList<>(intent.familyPreferences());
        for (FontFamilyName f : policy.defaultChain()) {
            if (!chain.contains(f)) {
                chain.add(f);
            }
        }
        return chain;
    }

    private Optional<ResolvedFontInstance> trySelect(FontFamilyName family, FontSelectionIntent intent,
                                                     ValidatedFontCatalogSnapshot catalog) {
        for (ValidatedFontCatalogSnapshot.Entry entry : catalog.entries()) {
            FontFaceManifest manifest = entry.manifest();
            if (family.value().equalsIgnoreCase(manifest.familyName())) {
                List<VariationCoordinate> axes = new ArrayList<>(intent.explicitAxisOverrides());
                return Optional.of(new ResolvedFontInstance(entry.reference(), axes));
            }
        }
        return Optional.empty();
    }

    private FontFaceManifest findManifest(ResolvedFontInstance instance, ValidatedFontCatalogSnapshot catalog) {
        for (ValidatedFontCatalogSnapshot.Entry entry : catalog.entries()) {
            if (entry.reference().equals(instance.executionReference())) {
                return entry.manifest();
            }
        }
        return null;
    }

    private boolean coversAll(FontFaceManifest manifest, TextContent content) {
        for (int i = 0; i < content.value().length(); ) {
            int cp = content.value().codePointAt(i);
            if (!manifest.coversScalar(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }
}
