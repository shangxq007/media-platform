package com.example.platform.render.domain.timeline.semantics.relationship;

import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SR13/IR2): persistent N-ary
 * semantic group. Flat (no nesting), set semantics, typed members.
 *
 * <p>GroupId is stable and independent from membership; membership changes
 * preserve identity (diff = GROUP_MEMBER_ADDED/REMOVED, never group
 * remove+add). Empty and single-member groups are rejected (frozen SR13).
 */
public record GroupRelationship(GroupId groupId, Set<TimelineClipId> members)
        implements SemanticRelationship {

    public GroupRelationship {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(members, "members");
        if (members.size() < 2) {
            throw new IllegalArgumentException("group must have at least 2 members (frozen SR13 cardinality)");
        }
    }

    public static GroupRelationship of(GroupId groupId, List<TimelineClipId> members) {
        Set<TimelineClipId> set = new TreeSet<>(members); // deterministic normalized order
        return new GroupRelationship(groupId, set);
    }

    /** Deterministic canonical member ordering (by TimelineClipId value). */
    public List<TimelineClipId> orderedMembers() {
        return List.copyOf(new TreeSet<>(members));
    }

    @Override
    public Kind kind() {
        return Kind.GROUP;
    }
}
