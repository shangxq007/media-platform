package com.example.platform.timeline.diff;

import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_POST_CLOSE (assertion A): relationship
 * semantic diff is TYPED in production TimelineDiffEngine — hash change is
 * never the diff oracle. Sync matched by normalized endpoint identity, Group
 * by GroupId, typed delta surfaced per semantic change.
 */
class TimelineRelationshipDiffTest {

    private static final TimelineClipId A = TimelineClipId.of("clip-a");
    private static final TimelineClipId B = TimelineClipId.of("clip-b");
    private static final TimelineClipId C = TimelineClipId.of("clip-c");

    private static TimelineClip clip(String id) {
        return new TimelineClip(id, "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(0, 1), MediaTime.ofRational(3, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
    }

    private static TimelineDocument doc(SemanticRelationship... rels) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO,
                        List.of(clip("clip-a"), clip("clip-b"), clip("clip-c")))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                List.of(rels));
    }

    private static List<TimelineChange> diff(TimelineDocument base, TimelineDocument target) {
        return TimelineDiffEngine.diff("p1", "r-base", "r-target", "d-base", "d-target", base, target).getChanges();
    }

    private static boolean hasType(List<TimelineChange> changes, ChangeType type) {
        return changes.stream().anyMatch(c -> c.getChangeType() == type);
    }

    @Test
    void syncAddIsTypedRelationshipAddition() {
        List<TimelineChange> changes = diff(doc(),
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO)));
        assertTrue(hasType(changes, ChangeType.RELATIONSHIP_ADDED),
                "sync add must surface as typed RELATIONSHIP_ADDED, not just hash change");
    }

    @Test
    void syncRemoveIsTypedRelationshipRemoval() {
        List<TimelineChange> changes = diff(
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO)),
                doc());
        assertTrue(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
    }

    @Test
    void syncAnchorChangeIsTyped() {
        List<TimelineChange> changes = diff(
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO)),
                doc(SyncRelationship.of(A, MediaTime.ofRational(1, 1), B, MediaTime.ZERO)));
        assertTrue(hasType(changes, ChangeType.SYNC_ANCHOR_CHANGED),
                "anchor change must be SYNC_ANCHOR_CHANGED, not remove+add");
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_ADDED));
    }

    @Test
    void syncReversedInputIsNoDiff() {
        List<TimelineChange> changes = diff(
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ofRational(2, 1))),
                doc(SyncRelationship.of(B, MediaTime.ofRational(2, 1), A, MediaTime.ZERO)));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_ADDED));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
        assertFalse(hasType(changes, ChangeType.SYNC_ANCHOR_CHANGED));
    }

    @Test
    void groupAddAndMemberAddAreTyped() {
        List<TimelineChange> changes = diff(
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B))),
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B, C))));
        assertTrue(hasType(changes, ChangeType.GROUP_MEMBER_ADDED),
                "same GroupId membership growth must be GROUP_MEMBER_ADDED(C)");
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_ADDED));
    }

    @Test
    void groupMemberRemoveIsTyped() {
        List<TimelineChange> changes = diff(
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B, C))),
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B))));
        assertTrue(hasType(changes, ChangeType.GROUP_MEMBER_REMOVED));
    }

    @Test
    void groupAddIsTypedRelationshipAddition() {
        List<TimelineChange> changes = diff(doc(),
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B))));
        assertTrue(hasType(changes, ChangeType.RELATIONSHIP_ADDED));
    }

    @Test
    void groupRemoveIsTypedRelationshipRemoval() {
        List<TimelineChange> changes = diff(
                doc(GroupRelationship.of(GroupId.of("g1"), List.of(A, B))),
                doc());
        assertTrue(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
    }

    @Test
    void identicalRelationshipsProduceNoRelationshipChanges() {
        List<TimelineChange> changes = diff(
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO),
                        GroupRelationship.of(GroupId.of("g1"), List.of(A, B))),
                doc(SyncRelationship.of(A, MediaTime.ZERO, B, MediaTime.ZERO),
                        GroupRelationship.of(GroupId.of("g1"), List.of(A, B))));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_ADDED));
        assertFalse(hasType(changes, ChangeType.RELATIONSHIP_REMOVED));
        assertFalse(hasType(changes, ChangeType.SYNC_ANCHOR_CHANGED));
        assertFalse(hasType(changes, ChangeType.GROUP_MEMBER_ADDED));
        assertFalse(hasType(changes, ChangeType.GROUP_MEMBER_REMOVED));
    }
}
