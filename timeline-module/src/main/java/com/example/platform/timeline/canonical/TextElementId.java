package com.example.platform.timeline.canonical;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * P2-5.5: DETERMINISTIC identity from a stable seed. Same seed =&gt; same
     * TextElementId (reproducible plan digest for preview/apply); different
     * seed =&gt; different id. The first 16 bytes of the SHA-256 of the seed are
     * formatted as a UUID string. {@link #random()} is unchanged.
     */
    public static TextElementId fromSeed(String seed) {
        Objects.requireNonNull(seed, "seed");
        if (seed.isBlank()) {
            throw new IllegalArgumentException("seed must not be blank");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            long mostSignificant = 0L;
            long leastSignificant = 0L;
            for (int i = 0; i < 8; i++) {
                mostSignificant = (mostSignificant << 8) | (hash[i] & 0xffL);
            }
            for (int i = 8; i < 16; i++) {
                leastSignificant = (leastSignificant << 8) | (hash[i] & 0xffL);
            }
            return new TextElementId(new UUID(mostSignificant, leastSignificant).toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String value() { return value; }

    @Override
    public boolean equals(Object o) { return o instanceof TextElementId t && value.equals(t.value); }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value; }
}
