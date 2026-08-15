package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C18): exact font size (Rational units). Zero double. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class FontSize {

    private final FontRational value;

    public FontSize(FontRational value) {
        this.value = Objects.requireNonNull(value, "value");
        if (value.numerator().signum() <= 0) {
            throw new IllegalArgumentException("font size must be > 0");
        }
    }

    public FontRational value() { return value; }

    @Override
    public boolean equals(Object o) { return o instanceof FontSize s && value.equals(s.value); }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return "fs:" + value; }
}
