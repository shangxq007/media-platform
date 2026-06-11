package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Set;

public interface MissingGlyphDetector {

    String detectorName();

    default boolean enabled() { return true; }

    List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints);

    record MissingGlyphsReport(
            String fontId,
            Set<Integer> requiredCodePoints,
            List<MissingGlyph> missingGlyphs,
            boolean hasMissing
    ) {}
}
