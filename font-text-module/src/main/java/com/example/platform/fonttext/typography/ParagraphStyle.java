package com.example.platform.fonttext.typography;

import com.example.platform.fonttext.text.ParagraphBaseDirection;
import java.util.Objects;

/** ROADMAP_19 (C19/C34/C43/C45): paragraph style — SOLE lineHeight + baseDirection authority. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class ParagraphStyle {

    public enum Alignment { START, END, CENTER, JUSTIFIED, LEFT, RIGHT }
    public enum Justification { NONE, INTER_WORD, INTER_CHARACTER }
    public enum WrapPolicy { WRAP, NO_WRAP }
    public enum LineBreakPolicy { STANDARD, STRICT }

    private final Alignment alignment;
    private final Justification justification;
    private final LineHeight lineHeight;
    private final WrapPolicy wrapPolicy;
    private final ParagraphBaseDirection baseDirection;
    private final LineBreakPolicy lineBreakPolicy;

    @com.fasterxml.jackson.annotation.JsonCreator
public ParagraphStyle(@com.fasterxml.jackson.annotation.JsonProperty("alignment") Alignment alignment, @com.fasterxml.jackson.annotation.JsonProperty("justification") Justification justification, @com.fasterxml.jackson.annotation.JsonProperty("lineHeight") LineHeight lineHeight, @com.fasterxml.jackson.annotation.JsonProperty("wrapPolicy") WrapPolicy wrapPolicy, @com.fasterxml.jackson.annotation.JsonProperty("baseDirection") ParagraphBaseDirection baseDirection, @com.fasterxml.jackson.annotation.JsonProperty("lineBreakPolicy") LineBreakPolicy lineBreakPolicy) {
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        this.justification = Objects.requireNonNull(justification, "justification");
        this.lineHeight = Objects.requireNonNull(lineHeight, "lineHeight");
        this.wrapPolicy = Objects.requireNonNull(wrapPolicy, "wrapPolicy");
        this.baseDirection = Objects.requireNonNull(baseDirection, "baseDirection");
        this.lineBreakPolicy = Objects.requireNonNull(lineBreakPolicy, "lineBreakPolicy");
    }

    public Alignment alignment() { return alignment; }
    public Justification justification() { return justification; }
    public LineHeight lineHeight() { return lineHeight; }
    public WrapPolicy wrapPolicy() { return wrapPolicy; }
    public ParagraphBaseDirection baseDirection() { return baseDirection; }
    public LineBreakPolicy lineBreakPolicy() { return lineBreakPolicy; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParagraphStyle p)) return false;
        return alignment == p.alignment && justification == p.justification && lineHeight.equals(p.lineHeight)
                && wrapPolicy == p.wrapPolicy && baseDirection == p.baseDirection
                && lineBreakPolicy == p.lineBreakPolicy;
    }

    @Override
    public int hashCode() { return Objects.hash(alignment, justification, lineHeight, wrapPolicy, baseDirection, lineBreakPolicy); }
}
