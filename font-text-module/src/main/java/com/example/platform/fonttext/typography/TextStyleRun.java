package com.example.platform.fonttext.typography;

import com.example.platform.fonttext.text.TextRange;
import java.util.Objects;

/** ROADMAP_19 (C17/C36): non-overlapping canonical style run. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextStyleRun {

    private final TextRange range;
    private final TextStyle style;

    public TextStyleRun(TextRange range, TextStyle style) {
        this.range = Objects.requireNonNull(range, "range");
        this.style = Objects.requireNonNull(style, "style");
    }

    public TextRange range() { return range; }
    public TextStyle style() { return style; }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextStyleRun r && range.equals(r.range) && style.equals(r.style);
    }

    @Override
    public int hashCode() { return Objects.hash(range, style); }
}
