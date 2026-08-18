package com.example.platform.fonttext.text;

import java.util.Objects;

/** ROADMAP_19 (C13): extensible BCP-47-compatible typed language tag. Zero closed enum. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class LanguageTag {

    private final String tag;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
public LanguageTag(String tag) {
        Objects.requireNonNull(tag, "tag");
        String t = tag.trim();
        if (t.isEmpty() || !t.matches("[a-zA-Z]{2,8}(-[a-zA-Z0-9]{1,8})*")) {
            throw new IllegalArgumentException("invalid BCP-47 language tag: " + tag);
        }
        this.tag = t;
    }

    public static LanguageTag of(String tag) { return new LanguageTag(tag); }

    public String value() { return tag; }

    @Override
    public boolean equals(Object o) { return o instanceof LanguageTag l && tag.equalsIgnoreCase(l.tag); }

    @Override
    public int hashCode() { return tag.toLowerCase().hashCode(); }

    @Override
    public String toString() { return tag; }
}
