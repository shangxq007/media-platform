package com.example.platform.fonttext.text;

import java.util.Objects;

/** ROADMAP_19 (C14): typed extensible ISO-15924-compatible script tag. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class ScriptTag {

    private final String tag;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
public ScriptTag(String tag) {
        Objects.requireNonNull(tag, "tag");
        String t = tag.trim();
        if (!t.matches("[A-Za-z]{4}")) {
            throw new IllegalArgumentException("ISO-15924 script tag must be 4 letters: " + tag);
        }
        this.tag = t;
    }

    public static final ScriptTag LATIN = new ScriptTag("Latn");
    public static final ScriptTag ARABIC = new ScriptTag("Arab");
    public static final ScriptTag HAN = new ScriptTag("Hani");
    public static final ScriptTag HIRAGANA = new ScriptTag("Hira");
    public static final ScriptTag KATAKANA = new ScriptTag("Kana");
    public static final ScriptTag CYRILLIC = new ScriptTag("Cyrl");
    public static final ScriptTag DEVANAGARI = new ScriptTag("Deva");
    public static final ScriptTag COMMON = new ScriptTag("Zyyy");
    public static final ScriptTag INHERITED = new ScriptTag("Zinh");

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() { return tag; }

    @Override
    public boolean equals(Object o) { return o instanceof ScriptTag s && tag.equalsIgnoreCase(s.tag); }

    @Override
    public int hashCode() { return tag.toLowerCase().hashCode(); }

    @Override
    public String toString() { return tag; }
}
