package com.example.platform.timeline.semantics.relationship;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClipId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** L5-V2 Gate 5: canonical identity of an alignment is `alignment:<alignmentId>`. */
class AlignmentCanonicalKeyTest {

    private static AlignmentRelationship alignment(String id, String... derived) {
        Set<SourceUnitId> d = new java.util.TreeSet<>();
        for (String v : derived) {
            d.add(SourceUnitId.of(v));
        }
        return new AlignmentRelationship(AlignmentId.of(id), Set.of(SourceUnitId.of("s1")), d,
                new SourceBaseline("rev-1", "digest-1"), List.of());
    }

    @Test
    void canonicalKeyIsAlignmentPrefixedId() {
        assertEquals("alignment:align-1",
                RelationshipCanonicalSemantics.canonicalKey(alignment("align-1", "d1")));
    }

    @Test
    void identityIsStableWhenEndpointsChange() {
        assertEquals(
                RelationshipCanonicalSemantics.canonicalKey(alignment("align-1", "d1")),
                RelationshipCanonicalSemantics.canonicalKey(alignment("align-1", "d1", "d2")),
                "alignment identity is alignmentId only; endpoints do not participate");
    }

    @Test
    void distinctAlignmentIdsHaveDistinctKeys() {
        assertNotEquals(
                RelationshipCanonicalSemantics.canonicalKey(alignment("a1", "d1")),
                RelationshipCanonicalSemantics.canonicalKey(alignment("a2", "d1")));
    }

    @Test
    void alignmentKeyDoesNotCollideWithGroupOrSyncGrammar() {
        String alignmentKey = RelationshipCanonicalSemantics.canonicalKey(alignment("g1", "d1"));
        assertNotEquals("group:g1", alignmentKey);
        assertNotEquals(
                RelationshipCanonicalSemantics.canonicalKey(
                        SyncRelationship.of(TimelineClipId.of("c1"), MediaTime.ZERO, TimelineClipId.of("c2"), MediaTime.ZERO)),
                alignmentKey);
    }
}
