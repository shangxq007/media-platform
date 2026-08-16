package com.example.platform.timeline.semantics.relationship;

import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.ScopeResolver;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1: Sync normalization/identity,
 * GroupId stability, canonical hash participation, revision-bound scope
 * resolution with explicit expansion.
 */
class SemanticRelationshipSelectionTest {

    private static final TimelineClipId A = TimelineClipId.of("clip-a");
    private static final TimelineClipId B = TimelineClipId.of("clip-b");
    private static final TimelineClipId C = TimelineClipId.of("clip-c");

    private static TimelineClip clip(String id, int startSec, int endSec) {
        return new TimelineClip(id, "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(startSec, 1), MediaTime.ofRational(endSec, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
    }

    private static TimelineDocument doc(List<? extends SemanticRelationship> rels) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO,
                        List.of(clip("clip-a", 0, 3), clip("clip-b", 3, 6), clip("clip-c", 6, 9)))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                rels.stream().map(r -> (SemanticRelationship) r).toList());
    }

    // ---- SYNC normalization / identity ----
    @Test
    void syncNormalizesSymmetricPair() {
        SyncRelationship s1 = SyncRelationship.of(A, MediaTime.ofRational(1, 1), B, MediaTime.ofRational(2, 1));
        SyncRelationship s2 = SyncRelationship.of(B, MediaTime.ofRational(2, 1), A, MediaTime.ofRational(1, 1));
        assertEquals(s1, s2, "A<->B == B<->A (anchors move with endpoints)");
        assertEquals(s1.identityKey(), s2.identityKey());
    }

    @Test
    void syncIdentityExcludesAnchors() {
        SyncRelationship s1 = SyncRelationship.of(A, MediaTime.ofRational(1, 1), B, MediaTime.ofRational(2, 1));
        SyncRelationship s2 = SyncRelationship.of(A, MediaTime.ofRational(3, 1), B, MediaTime.ofRational(4, 1));
        assertEquals(s1.identityKey(), s2.identityKey(), "same pair -> same identity");
        assertNotEquals(s1, s2, "different anchors -> different content");
    }

    @Test
    void syncSelfEdgeAndDuplicatePairRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SyncRelationship.of(A, MediaTime.ZERO, A, MediaTime.ZERO));
    }

    // ---- GROUP ----
    @Test
    void groupIdStableAcrossMembershipChange() {
        GroupId gid = GroupId.of("group-1");
        GroupRelationship g1 = GroupRelationship.of(gid, List.of(A, B));
        GroupRelationship g2 = GroupRelationship.of(gid, List.of(A, B, C));
        assertEquals(g1.groupId(), g2.groupId(), "GroupId stable under membership change");
        assertNotEquals(g1.members(), g2.members());
    }

    @Test
    void groupMemberOrderIrrelevant() {
        assertEquals(GroupRelationship.of(GroupId.of("g"), List.of(A, B, C)),
                GroupRelationship.of(GroupId.of("g"), List.of(C, A, B)),
                "construction order semantically irrelevant");
    }

    @Test
    void groupCardinalityEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> GroupRelationship.of(GroupId.of("g"), List.of(A)));
    }

    // ---- CANONICAL HASH ----
    @Test
    void relationshipsParticipateInContentHash() {
        TimelineContentDigester digester = new TimelineContentDigester();
        String hNone = digester.digest(doc(List.of()));
        String hSync = digester.digest(doc(List.of(
                SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO))));
        assertNotEquals(hNone, hSync, "adding a sync must change the hash");

        String hSyncRev = digester.digest(doc(List.of(
                SyncRelationship.of(B, MediaTime.ZERO, A, MediaTime.ZERO))));
        assertEquals(hSync, hSyncRev, "reversed endpoint input -> identical hash");

        String hSyncAnchor = digester.digest(doc(List.of(
                SyncRelationship.of(A, MediaTime.ofRational(1, 1), B, MediaTime.ZERO))));
        assertNotEquals(hSync, hSyncAnchor, "anchor change must change the hash");
    }

    @Test
    void groupMembershipOrderDoesNotChangeHash() {
        TimelineContentDigester digester = new TimelineContentDigester();
        String h1 = digester.digest(doc(List.of(GroupRelationship.of(GroupId.of("g"), List.of(A, B, C)))));
        String h2 = digester.digest(doc(List.of(GroupRelationship.of(GroupId.of("g"), List.of(C, A, B)))));
        assertEquals(h1, h2, "member construction order -> same hash");
    }

    // ---- SCOPE RESOLUTION ----
    @Test
    void exactExpansionReturnsOnlySelected() {
        TimelineDocument d = doc(List.of());
        ResolvedScope scope = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.ExplicitObjectSelection(List.of(A)),
                SelectionSpec.ExpansionPolicy.EXACT);
        assertEquals(List.of(A), scope.resolvedClipIds());
        assertEquals("rev-1", scope.baseRevisionId());
        assertEquals(SelectionSpec.ExpansionPolicy.EXACT, scope.expansionPolicy());
    }

    @Test
    void groupExpansionAddsDirectMembers() {
        TimelineDocument d = doc(List.of(GroupRelationship.of(GroupId.of("g"), List.of(A, B))));
        ResolvedScope scope = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.ExplicitObjectSelection(List.of(A)),
                SelectionSpec.ExpansionPolicy.EXPAND_GROUP);
        assertEquals(List.of(A, B), scope.resolvedClipIds());
    }

    @Test
    void syncExpansionAddsEndpoint() {
        TimelineDocument d = doc(List.of(SyncRelationship.of(A, MediaTime.ZERO, C, MediaTime.ZERO)));
        ResolvedScope scope = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.ExplicitObjectSelection(List.of(A)),
                SelectionSpec.ExpansionPolicy.EXPAND_SYNC);
        assertTrue(scope.resolvedClipIds().contains(C));
    }

    @Test
    void timeRangeSelectionIntersects() {
        TimelineDocument d = doc(List.of());
        ResolvedScope scope = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.TimelineTimeRangeSelection(MediaTime.ofRational(2, 1), MediaTime.ofRational(7, 1)),
                SelectionSpec.ExpansionPolicy.EXACT);
        assertEquals(List.of(A, B, C), scope.resolvedClipIds(), "clips [0,3),[3,6),[6,9) each intersect [2,7)");
    }

    @Test
    void missingSelectionTargetFailsClosed() {
        TimelineDocument d = doc(List.of());
        assertThrows(IllegalArgumentException.class, () ->
                ScopeResolver.resolve(d, "rev-1", "hash-1",
                        new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("nope"))),
                        SelectionSpec.ExpansionPolicy.EXACT));
    }

    @Test
    void scopeResolutionIsDeterministic() {
        TimelineDocument d = doc(List.of(SyncRelationship.of(A, MediaTime.ZERO, C, MediaTime.ZERO),
                GroupRelationship.of(GroupId.of("g"), List.of(A, B))));
        ResolvedScope s1 = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.ExplicitObjectSelection(List.of(A)),
                SelectionSpec.ExpansionPolicy.EXPAND_SYNC);
        ResolvedScope s2 = ScopeResolver.resolve(d, "rev-1", "hash-1",
                new SelectionSpec.ExplicitObjectSelection(List.of(A)),
                SelectionSpec.ExpansionPolicy.EXPAND_SYNC);
        assertEquals(s1, s2);
        assertEquals(List.of(A, C), s1.resolvedClipIds(), "placement order then clip id, deduped");
    }
}
