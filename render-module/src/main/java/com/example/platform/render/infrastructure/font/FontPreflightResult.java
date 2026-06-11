package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontPreflightResult(
        boolean passed,
        List<String> fontAssetIds,
        List<FontManifestResolver.ResolvedFont> resolvedFonts,
        List<MissingGlyph> missingGlyphs,
        boolean fallbackUsed,
        List<String> warnings,
        List<String> errors,
        boolean productionSafe,
        boolean subsetRequired,
        List<String> subsetUrls
) {
    public static FontPreflightResult failed(List<String> errors) {
        return new FontPreflightResult(false, List.of(), List.of(), List.of(),
                false, List.of(), errors, false, false, List.of());
    }

    public static FontPreflightResult passed(List<String> fontAssetIds,
            List<FontManifestResolver.ResolvedFont> resolvedFonts, List<String> warnings) {
        return new FontPreflightResult(true, fontAssetIds, resolvedFonts, List.of(),
                false, warnings, List.of(), true, false, List.of());
    }
}
