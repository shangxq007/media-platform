package com.example.platform.timeline.semantics.relationship;

import java.util.Objects;

/**
 * L5 lineage: language-neutral, revision-stable identity of a canonical semantic source unit.
 *
 * <p>Stable across Timeline revisions for the same logical unit; never derived from language,
 * text content, position or display label. Mirrors {@link GroupId} (typed, non-blank,
 * deterministic equality/ordering).
 */
public record SourceUnitId(String value) implements Comparable<SourceUnitId> {

    public SourceUnitId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SourceUnitId must not be blank");
        }
    }

    public static SourceUnitId of(String value) {
        return new SourceUnitId(value);
    }

    @Override
    public int compareTo(SourceUnitId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
