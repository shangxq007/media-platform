package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C43/R3): exact line-height — ratio or absolute; zero CSS strings/double. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class LineHeight {

    public enum Form { RATIO, ABSOLUTE }

    private final Form form;
    private final FontRational value;

    public LineHeight(Form form, FontRational value) {
        this.form = Objects.requireNonNull(form, "form");
        this.value = Objects.requireNonNull(value, "value");
        if (value.numerator().signum() <= 0) {
            throw new IllegalArgumentException("line height must be > 0");
        }
    }

    public static LineHeight ratio(FontRational ratio) { return new LineHeight(Form.RATIO, ratio); }
    public static LineHeight absolute(FontRational length) { return new LineHeight(Form.ABSOLUTE, length); }

    public Form form() { return form; }
    public FontRational value() { return value; }

    @Override
    public boolean equals(Object o) { return o instanceof LineHeight h && form == h.form && value.equals(h.value); }

    @Override
    public int hashCode() { return Objects.hash(form, value); }
}
