package com.example.platform.timeline.canonical;

import java.util.Objects;
import java.util.UUID;

/** ROADMAP_19 (C50): typed stable TextElement identity. Zero raw String identity. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class TextElementId {

    private final String value;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public TextElementId(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TextElementId must not be blank");
        }
        this.value = value;
    }

    public static TextElementId random() { return new TextElementId(UUID.randomUUID().toString()); }

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() { return value; }

    @Override
    public boolean equals(Object o) { return o instanceof TextElementId t && value.equals(t.value); }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
