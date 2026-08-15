package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C20/C35): authored layout intent — width/height/alignment/wrap/overflow. Zero glyph geometry. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class TextFrame {

    public enum HorizontalAlignment { START, END, CENTER }
    public enum VerticalAlignment { TOP, CENTER, BOTTOM }
    public enum OverflowBehavior { CLIP, VISIBLE, SCROLL }

    private final FontRational widthConstraint;
    private final FontRational heightConstraint; // null = unbounded
    private final HorizontalAlignment horizontalAlignment;
    private final VerticalAlignment verticalAlignment;
    private final ParagraphStyle.WrapPolicy wrapBehavior;
    private final OverflowBehavior overflowBehavior;

    public TextFrame(FontRational widthConstraint, FontRational heightConstraint,
                     HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment,
                     ParagraphStyle.WrapPolicy wrapBehavior, OverflowBehavior overflowBehavior) {
        this.widthConstraint = Objects.requireNonNull(widthConstraint, "widthConstraint");
        this.heightConstraint = heightConstraint;
        this.horizontalAlignment = Objects.requireNonNull(horizontalAlignment, "horizontalAlignment");
        this.verticalAlignment = Objects.requireNonNull(verticalAlignment, "verticalAlignment");
        this.wrapBehavior = Objects.requireNonNull(wrapBehavior, "wrapBehavior");
        this.overflowBehavior = Objects.requireNonNull(overflowBehavior, "overflowBehavior");
        if (widthConstraint.numerator().signum() <= 0) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (heightConstraint != null && heightConstraint.numerator().signum() <= 0) {
            throw new IllegalArgumentException("height must be > 0 when present");
        }
    }

    public FontRational widthConstraint() { return widthConstraint; }
    public FontRational heightConstraint() { return heightConstraint; }
    public HorizontalAlignment horizontalAlignment() { return horizontalAlignment; }
    public VerticalAlignment verticalAlignment() { return verticalAlignment; }
    public ParagraphStyle.WrapPolicy wrapBehavior() { return wrapBehavior; }
    public OverflowBehavior overflowBehavior() { return overflowBehavior; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextFrame f)) return false;
        return widthConstraint.equals(f.widthConstraint) && Objects.equals(heightConstraint, f.heightConstraint)
                && horizontalAlignment == f.horizontalAlignment && verticalAlignment == f.verticalAlignment
                && wrapBehavior == f.wrapBehavior && overflowBehavior == f.overflowBehavior;
    }

    @Override
    public int hashCode() {
        return Objects.hash(widthConstraint, heightConstraint, horizontalAlignment, verticalAlignment, wrapBehavior, overflowBehavior);
    }
}
