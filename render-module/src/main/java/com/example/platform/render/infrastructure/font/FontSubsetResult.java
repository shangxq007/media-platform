package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;

public record FontSubsetResult(
        String strategy,
        boolean cacheable,
        String cacheKey,
        String subsetUri,
        String subsetFormat,
        long subsetSize,
        int originalGlyphCount,
        int subsetGlyphCount,
        List<MissingGlyph> missingGlyphs,
        Map<String, String> fallbackChains
) {
    public boolean hasMissingGlyphs() {
        return missingGlyphs != null && !missingGlyphs.isEmpty();
    }
}
