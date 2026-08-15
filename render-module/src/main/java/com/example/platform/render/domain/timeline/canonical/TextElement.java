package com.example.platform.render.domain.timeline.canonical;

import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C34/C50): Timeline-owned authored TextElement (OPTION T1).
 * Exact font resolution is frozen in ResolvedFontRuns (historical never
 * re-resolves). No SourceBinding; no ShapedGlyphRun; timing is exact Rational.
 */
public class TextElement {

    @JsonProperty("id")
    private final TextElementId id;

    @JsonProperty("start")
    private final FontRational start;

    @JsonProperty("duration")
    private final FontRational duration;

    @JsonProperty("styledText")
    private final StyledText styledText;

    @JsonProperty("frame")
    private final TextFrame frame;

    @JsonProperty("fallbackPolicy")
    private final FontFallbackPolicy fallbackPolicy;

    @JsonProperty("resolvedFontRuns")
    private final List<ResolvedFontRun> resolvedFontRuns;

    @JsonCreator
    public TextElement(
            @JsonProperty("id") TextElementId id,
            @JsonProperty("start") FontRational start,
            @JsonProperty("duration") FontRational duration,
            @JsonProperty("styledText") StyledText styledText,
            @JsonProperty("frame") TextFrame frame,
            @JsonProperty("fallbackPolicy") FontFallbackPolicy fallbackPolicy,
            @JsonProperty("resolvedFontRuns") List<ResolvedFontRun> resolvedFontRuns) {
        this.id = Objects.requireNonNull(id, "id");
        this.start = Objects.requireNonNull(start, "start");
        this.duration = Objects.requireNonNull(duration, "duration");
        this.styledText = Objects.requireNonNull(styledText, "styledText");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
        this.resolvedFontRuns = List.copyOf(resolvedFontRuns);
        if (duration.numerator().signum() <= 0) {
            throw new IllegalArgumentException("duration must be > 0");
        }
    }

    public TextElementId id() { return id; }
    public FontRational start() { return start; }
    public FontRational duration() { return duration; }
    public StyledText styledText() { return styledText; }
    public TextFrame frame() { return frame; }
    public FontFallbackPolicy fallbackPolicy() { return fallbackPolicy; }
    public List<ResolvedFontRun> resolvedFontRuns() { return resolvedFontRuns; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextElement e)) return false;
        return id.equals(e.id) && start.equals(e.start) && duration.equals(e.duration)
                && styledText.equals(e.styledText) && frame.equals(e.frame)
                && fallbackPolicy.equals(e.fallbackPolicy) && resolvedFontRuns.equals(e.resolvedFontRuns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, start, duration, styledText, frame, fallbackPolicy, resolvedFontRuns);
    }
}
