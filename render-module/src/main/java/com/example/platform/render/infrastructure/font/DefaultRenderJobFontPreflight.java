package com.example.platform.render.infrastructure.font;

import com.example.platform.render.infrastructure.RenderConstraints;
import com.example.platform.render.infrastructure.RenderJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class DefaultRenderJobFontPreflight implements RenderJobFontPreflight {
    private static final Logger log = LoggerFactory.getLogger(DefaultRenderJobFontPreflight.class);

    private final FontAssetRepository assetRepository;
    private final FontManifestResolver manifestResolver;
    private final MissingGlyphDetector missingGlyphDetector;
    private final FontStackResolver fontStackResolver;

    public DefaultRenderJobFontPreflight(FontAssetRepository assetRepository,
                                          FontManifestResolver manifestResolver,
                                          MissingGlyphDetector missingGlyphDetector,
                                          FontStackResolver fontStackResolver) {
        this.assetRepository = assetRepository;
        this.manifestResolver = manifestResolver;
        this.missingGlyphDetector = missingGlyphDetector;
        this.fontStackResolver = fontStackResolver;
    }

    @Override
    public FontPreflightResult preflight(RenderJob job) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Set<String> fontAssetIds = collectFontAssetIds(job);
        if (fontAssetIds.isEmpty()) {
            return FontPreflightResult.passed(List.of(), List.of(), List.of());
        }

        List<FontManifestResolver.ResolvedFont> resolvedFonts = new ArrayList<>();
        List<MissingGlyph> allMissingGlyphs = new ArrayList<>();
        boolean anyFallbackUsed = false;
        boolean anyNotProductionSafe = false;
        boolean anySubsetRequired = false;
        List<String> subsetUrls = new ArrayList<>();
        List<String> resolvedAssetIds = new ArrayList<>();

        for (String fontAssetId : fontAssetIds) {
            FontAsset asset = assetRepository.findById(fontAssetId).orElse(null);
            if (asset == null) {
                errors.add("Font asset not found: " + fontAssetId);
                continue;
            }

            if (!asset.isReadyForRender()) {
                errors.add("Font asset not ready: " + fontAssetId + " (status=" + asset.status() + ")");
                continue;
            }

            if (asset.securityResult() == null || !"PASSED".equals(asset.securityResult().scanStatus())) {
                errors.add("Font security check not passed: " + fontAssetId);
                continue;
            }

            if (!asset.securityResult().productionSafe()) {
                if ("production".equals(job.mode())) {
                    errors.add("Font not production-safe in production mode: " + fontAssetId);
                    continue;
                }
                anyNotProductionSafe = true;
                warnings.add("Font not production-safe (allowed in " + job.mode() + " mode): " + fontAssetId);
            }

            if (asset.validationResult() != null && !"PASSED".equals(asset.validationResult().validationStatus())) {
                errors.add("Font validation not passed: " + fontAssetId + " (status=" + asset.validationResult().validationStatus() + ")");
                continue;
            }

                try {
                    FontManifestResolver.ResolvedFont primary = manifestResolver.resolvePrimary(fontAssetId, job.mode());
                resolvedFonts.add(primary);
                resolvedAssetIds.add(fontAssetId);

                if (primary.subsetUrl() != null) {
                    anySubsetRequired = true;
                    subsetUrls.add(primary.subsetUrl());
                }

                Set<Integer> requiredCodePoints = collectCodePointsForFont(job, fontAssetId);
                if (!requiredCodePoints.isEmpty()) {
                    List<MissingGlyph> missing = missingGlyphDetector.detectMissingGlyphs(fontAssetId, requiredCodePoints);
                    if (!missing.isEmpty()) {
                        allMissingGlyphs.addAll(missing);
                        if (job.allowDegrade()) {
                            List<FontManifestResolver.ResolvedFont> fallbacks =
                                    manifestResolver.resolveFallbackChain(fontAssetId, requiredCodePoints);
                            resolvedFonts.addAll(fallbacks.subList(1, fallbacks.size()));
                            anyFallbackUsed = true;
                            warnings.add("Missing glyphs resolved via fallback for font: " + fontAssetId);
                        } else {
                            errors.add("Missing glyphs detected and allowFallback=false: " + fontAssetId);
                        }
                    }
                }
            } catch (FontResolutionException e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("Font preflight failed for job {}: {}", job.id(), errors);
            return FontPreflightResult.failed(errors);
        }

        boolean productionSafe = !anyNotProductionSafe || !"production".equals(job.mode());
        return new FontPreflightResult(
                true, resolvedAssetIds, resolvedFonts, allMissingGlyphs,
                anyFallbackUsed, warnings, errors, productionSafe,
                anySubsetRequired, subsetUrls
        );
    }

    @Override
    public Set<String> collectFontAssetIds(RenderJob job) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        if (job.style() != null && job.style().contains("fontRef")) {
            ids.add(extractFontRef(job.style()));
        }
        if (job.captions() != null && job.captions().contains("fontRef")) {
            ids.add(extractFontRef(job.captions()));
        }
        if (job.style() != null && job.style().contains("templateRef")) {
            ids.add(extractFontRef(job.style()));
        }
        return ids;
    }

    private String extractFontRef(String json) {
        int idx = json.indexOf("\"fontRef\"");
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx);
        int startQuote = json.indexOf("\"", colonIdx);
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (startQuote < 0 || endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private Set<Integer> collectCodePointsForFont(RenderJob job, String fontAssetId) {
        Set<Integer> codePoints = new java.util.LinkedHashSet<>();
        if (job.captions() != null) {
            for (int i = 0; i < job.captions().length(); i++) {
                codePoints.add((int) job.captions().charAt(i));
            }
        }
        return codePoints;
    }
}
