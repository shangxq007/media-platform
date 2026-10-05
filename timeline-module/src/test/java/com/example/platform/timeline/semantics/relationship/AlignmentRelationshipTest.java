package com.example.platform.timeline.semantics.relationship;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** L5 lineage: AlignmentRelationship construction, endpoint cardinality, kind, identity. */
class AlignmentRelationshipTest {

    private static SourceBaseline baseline() {
        return new SourceBaseline("rev-1", "digest-1");
    }

    @Test
    void constructsAndExposesKindAndIdentity() {
        AlignmentRelationship rel = new AlignmentRelationship(
                AlignmentId.of("align-1"),
                Set.of(SourceUnitId.of("s1")),
                Set.of(SourceUnitId.of("d1")),
                baseline(), List.of());

        assertEquals(SemanticRelationship.Kind.ALIGNMENT, rel.kind());
        assertEquals("ALIGNMENT(align-1)", rel.identityKey());
        assertEquals("align-1", rel.alignmentId().value());
    }

    @Test
    void rejectsEmptySourceUnits() {
        assertThrows(IllegalArgumentException.class, () -> new AlignmentRelationship(
                AlignmentId.of("a"), Set.of(), Set.of(SourceUnitId.of("d1")), baseline(), List.of()));
    }

    @Test
    void rejectsEmptyDerivedUnits() {
        assertThrows(IllegalArgumentException.class, () -> new AlignmentRelationship(
                AlignmentId.of("a"), Set.of(SourceUnitId.of("s1")), Set.of(), baseline(), List.of()));
    }

    @Test
    void supportsOneToOneOneToManyManyToOneAndManyToMany() {
        assertEquals(1, aligned(1, 1).sourceUnits().size());
        assertEquals(2, aligned(1, 2).derivedUnits().size());
        assertEquals(2, aligned(2, 1).sourceUnits().size());
        assertEquals(3, aligned(3, 3).derivedUnits().size());
    }

    @Test
    void endpointSetsAreDeterministicallyOrdered() {
        AlignmentRelationship rel = new AlignmentRelationship(
                AlignmentId.of("a"),
                Set.of(SourceUnitId.of("s3"), SourceUnitId.of("s1"), SourceUnitId.of("s2")),
                Set.of(SourceUnitId.of("d2"), SourceUnitId.of("d1")),
                baseline(), List.of());

        assertEquals(List.of("s1", "s2", "s3"), rel.sourceUnits().stream().map(SourceUnitId::value).toList());
        assertEquals(List.of("d1", "d2"), rel.derivedUnits().stream().map(SourceUnitId::value).toList());
    }

    private static AlignmentRelationship aligned(int sources, int derived) {
        Set<SourceUnitId> s = new java.util.TreeSet<>();
        Set<SourceUnitId> d = new java.util.TreeSet<>();
        for (int i = 0; i < sources; i++) {
            s.add(SourceUnitId.of("s" + i));
        }
        for (int i = 0; i < derived; i++) {
            d.add(SourceUnitId.of("d" + i));
        }
        return new AlignmentRelationship(AlignmentId.of("a"), s, d, baseline(), List.of());
    }
}
