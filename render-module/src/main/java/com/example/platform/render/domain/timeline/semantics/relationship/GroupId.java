package com.example.platform.render.domain.timeline.semantics.relationship;

import java.util.Objects;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (IR2/SR13): typed stable
 * identity of a canonical semantic Group.
 *
 * <p>Stable, immutable, independent from the current member set; supplied once
 * at group creation; preserved when membership changes. NEVER derived from
 * members/ordering/timestamps/display name/UI selection.
 */
public record GroupId(String value) implements Comparable<GroupId> {

    public GroupId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("GroupId must not be blank");
        }
    }

    public static GroupId of(String value) {
        return new GroupId(value);
    }

    @Override
    public int compareTo(GroupId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
