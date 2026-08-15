package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.text.TextRange;
import java.util.Objects;

/** ROADMAP_19 (C45): canonical exact font assignment per text range. Zero glyph data. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class ResolvedFontRun {

    private final TextRange range;
    private final ResolvedFontInstance font;

    public ResolvedFontRun(TextRange range, ResolvedFontInstance font) {
        this.range = Objects.requireNonNull(range, "range");
        this.font = Objects.requireNonNull(font, "font");
    }

    public TextRange range() { return range; }
    public ResolvedFontInstance font() { return font; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ResolvedFontRun r && range.equals(r.range) && font.equals(r.font);
    }

    @Override
    public int hashCode() { return Objects.hash(range, font); }
}
