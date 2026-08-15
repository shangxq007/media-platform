package com.example.platform.fonttext.execution;

import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C32/C49): derived shaping result. NEVER Timeline canonical state
 * (SHAPED_GLYPH_RUN_IN_TIMELINE_CANONICAL_STATE = NO).
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class ShapedGlyphRun {

    public record Glyph(int glyphId, int cluster, long advanceNumerator, long advanceDenominator,
                        long offsetXNumerator, long offsetXDenominator, long offsetYNumerator, long offsetYDenominator) {}

    private final ResolvedFontInstance font;
    private final List<Glyph> glyphs;

    public ShapedGlyphRun(ResolvedFontInstance font, List<Glyph> glyphs) {
        this.font = Objects.requireNonNull(font, "font");
        this.glyphs = List.copyOf(glyphs);
    }

    public ResolvedFontInstance font() { return font; }
    public List<Glyph> glyphs() { return glyphs; }
}
