package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Set;

/**
 * FontTools-based missing glyph detector skeleton.
 *
 * Disabled by default. Enable via: render.font.tools.enabled=true
 */
public class FontToolsMissingGlyphDetector implements MissingGlyphDetector {

    private boolean enabled = false;

    public FontToolsMissingGlyphDetector enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public String detectorName() {
        return "FontToolsMissingGlyphDetector";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints) {
        return List.of();
    }
}
