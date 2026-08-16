package com.example.platform.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (IR1): typed stable canonical
 * identity of a TimelineClip.
 *
 * <p>Replaces raw String clip identity in canonical/domain code. Immutable,
 * non-null, non-blank, deterministic equality/ordering. Stable across Timeline
 * revisions for the same logical clip. Canonical external representation
 * remains the scalar string value (no nested JSON object). No mutable
 * position/index semantics.
 *
 * <p>No universal TimelineObjectId is introduced (future object kinds are a
 * future explicit architecture decision).
 */
public record TimelineClipId(String value) implements Comparable<TimelineClipId> {

    public TimelineClipId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TimelineClipId must not be blank");
        }
    }

    @JsonCreator
    public static TimelineClipId of(String value) {
        return new TimelineClipId(value);
    }

    /** Canonical external representation remains the scalar string value (IR1). */
    @JsonValue
    public String jsonValue() {
        return value;
    }

    @Override
    public int compareTo(TimelineClipId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
