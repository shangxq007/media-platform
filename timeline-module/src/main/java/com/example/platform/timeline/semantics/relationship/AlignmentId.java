package com.example.platform.timeline.semantics.relationship;

import java.util.Objects;

/**
 * L5 lineage: typed stable identity of an alignment (source units -> derived units).
 *
 * <p>Supplied once at alignment creation (caller-minted) and preserved across revisions and
 * membership changes; NEVER derived from the endpoint sets. Mirrors {@link GroupId}.
 */
public record AlignmentId(String value) implements Comparable<AlignmentId> {

    public AlignmentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AlignmentId must not be blank");
        }
    }

    public static AlignmentId of(String value) {
        return new AlignmentId(value);
    }

    @Override
    public int compareTo(AlignmentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
