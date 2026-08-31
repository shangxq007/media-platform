package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C26): typed extensible OpenType 4-char axis tag. Zero closed enum. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class VariationAxisTag implements Comparable<VariationAxisTag> {

    private final String tag;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
public VariationAxisTag(String tag) {
        Objects.requireNonNull(tag, "tag");
        String t = tag.trim();
        if (t.length() != 4 || !t.matches("[A-Za-z0-9]{4}")) {
            throw new IllegalArgumentException("axis tag must be exactly 4 tag chars: " + tag);
        }
        this.tag = t;
    }

    public static final VariationAxisTag WEIGHT = new VariationAxisTag("wght");
    public static final VariationAxisTag WIDTH = new VariationAxisTag("wdth");
    public static final VariationAxisTag OPTICAL_SIZE = new VariationAxisTag("opsz");
    public static final VariationAxisTag SLANT = new VariationAxisTag("slnt");
    public static final VariationAxisTag ITALIC = new VariationAxisTag("ital");

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() { return tag; }

    @Override
    public int compareTo(VariationAxisTag o) { return tag.compareTo(o.tag); }

    @Override
    public boolean equals(Object o) { return o instanceof VariationAxisTag v && tag.equals(v.tag); }

    @Override
    public int hashCode() { return tag.hashCode(); }

    @Override
    public String toString() { return tag; }
}
