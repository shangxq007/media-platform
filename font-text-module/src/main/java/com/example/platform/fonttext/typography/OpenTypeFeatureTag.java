package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C30): typed extensible 4-char OpenType feature tag. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class OpenTypeFeatureTag implements Comparable<OpenTypeFeatureTag> {

    private final String tag;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
public OpenTypeFeatureTag(String tag) {
        Objects.requireNonNull(tag, "tag");
        String t = tag.trim();
        if (t.length() != 4 || !t.matches("[A-Za-z0-9]{4}")) {
            throw new IllegalArgumentException("feature tag must be exactly 4 tag chars: " + tag);
        }
        this.tag = t;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() { return tag; }

    @Override
    public int compareTo(OpenTypeFeatureTag o) { return tag.compareTo(o.tag); }

    @Override
    public boolean equals(Object o) { return o instanceof OpenTypeFeatureTag f && tag.equals(f.tag); }

    @Override
    public int hashCode() { return tag.hashCode(); }

    @Override
    public String toString() { return tag; }
}
