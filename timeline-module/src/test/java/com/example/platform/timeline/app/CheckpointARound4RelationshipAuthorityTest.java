package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.TimelinePatch;
import com.example.platform.timeline.diff.TimelinePatchId;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.RelationshipCanonicalSemantics;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A Round 4 (R4-A3): SemanticRelationship local-authority behavior
 * tests.
 *
 * <p>Proves through the PRODUCTION diff → patch path:
 * <ul>
 *   <li>Group identity = groupId; Sync identity = normalized endpoint pair
 *       (reversed input → same identity)</li>
 *   <li>Group member delta and Sync anchor change are delegated to the
 *       Relationship-local authority (single domain authority)</li>
 *   <li>ADD / DELETE / MODIFY (member) / SYNC anchor / DIVERGENT /
 *       DELETE-vs-MODIFY are first-class diff/merge ops</li>
 *   <li>zero System.identityHashCode canonical identity fallback</li>
 * </ul>
 */
class CheckpointARound4RelationshipAuthorityTest {

    private static GroupRelationship group(String gid, String... members) {
        Set<TimelineClipId> m = new LinkedHashSet<>();
        for (String mm : members) {
            m.add(new TimelineClipId(mm));
        }
        return new GroupRelationship(new GroupId(gid), m);
    }

    private static SyncRelationship sync(String a, String b, long anchorA) {
        return SyncRelationship.of(new TimelineClipId(a), MediaTime.ofTicks(anchorA, 1),
                new TimelineClipId(b), MediaTime.ZERO);
    }

    private static CanonicalTimelineSnapshot snap(List<SemanticRelationship> rels, String rev) {
        return TimelineSnapshotConverter.toSnapshot(new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty(),
                AudioMix.empty(), rels, List.of()), rev);
    }

    private static CanonicalTimelineSnapshot withRels(CanonicalTimelineSnapshot base,
            List<SemanticRelationship> rels) {
        return new CanonicalTimelineSnapshot(base.id(), base.revisionId(), base.duration(),
                base.tracks(), base.captions(), base.watermarks(), base.templateApplications(),
                base.workflowSteps(), base.outputProfile(), base.safeMetadata(), base.textElements(),
                base.transitions(), base.automations(), base.audioMix(), rels);
    }

    private static List<com.example.platform.timeline.diff.TimelineChangeOperation> diff(
            CanonicalTimelineSnapshot b, CanonicalTimelineSnapshot a) {
        return new com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator()
                .calculate(b, a).diff().operations();
    }

    private static TimelinePatchApplicationResult apply(CanonicalTimelineSnapshot base,
            List<com.example.platform.timeline.diff.TimelineChangeOperation> ops) {
        return new TimelinePatchApplier().apply(base, new TimelinePatch(
                new TimelinePatchId("p"), base.revisionId(), ops, null, Map.of()));
    }

    @Test
    void groupAndSyncCanonicalIdentity() {
        // Group identity = groupId (members do NOT participate in identity)
        assertEquals(RelationshipCanonicalSemantics.canonicalKey(group("g1", "c1", "c2")),
                RelationshipCanonicalSemantics.canonicalKey(group("g1", "c1", "c2", "c3")),
                "Group identity is groupId only");
        // Sync identity = normalized endpoint pair — reversed input → SAME key
        assertEquals(RelationshipCanonicalSemantics.canonicalKey(sync("c1", "c2", 10)),
                RelationshipCanonicalSemantics.canonicalKey(sync("c2", "c1", 10)),
                "Sync identity must be order-independent (normalized)");
        // deterministic: identical relationship → identical key
        assertEquals(RelationshipCanonicalSemantics.canonicalKey(group("g1", "c1", "c2")),
                RelationshipCanonicalSemantics.canonicalKey(group("g1", "c1", "c2")),
                "canonical key must be deterministic");
        // The fail-closed path for unknown variants is enforced by the sealed
        // root (SemanticRelationship permits exactly Group + Sync) AND by the
        // source-level guard: zero System.identityHashCode in canonical identity
        // paths (verified by the Round-4 guard task over all production files).
    }

    @Test
    void groupMemberDeltaAndApplyDelegated() {
        GroupRelationship before = group("g1", "c1", "c2");
        GroupRelationship after = group("g1", "c1", "c2", "c3");
        var deltas = RelationshipCanonicalSemantics.groupMemberDelta(before, after);
        assertEquals(1, deltas.size());
        assertEquals(new TimelineClipId("c3"), deltas.get(0).member());
        assertTrue(deltas.get(0).added());

        // apply through the authority
        GroupRelationship applied = RelationshipCanonicalSemantics.applyGroupMemberChange(
                before, new TimelineClipId("c3"), true);
        assertEquals(after.members(), applied.members(), "apply must be delegated to authority");
        GroupRelationship removed = RelationshipCanonicalSemantics.applyGroupMemberChange(
                applied, new TimelineClipId("c2"), false);
        assertTrue(!removed.members().contains(new TimelineClipId("c2")));
    }

    @Test
    void syncAnchorChangeDetected() {
        SyncRelationship before = sync("c1", "c2", 10);
        SyncRelationship after = sync("c1", "c2", 25);
        assertTrue(RelationshipCanonicalSemantics.syncAnchorChanged(before, after),
                "anchor change must be detected by the authority");
    }

    @Test
    void relationshipAddDeleteModifyViaProductionPath() {
        CanonicalTimelineSnapshot empty = snap(List.of(), "r0");
        CanonicalTimelineSnapshot added = withRels(empty, List.of(group("g1", "c1", "c2")));

        // ADD
        List<com.example.platform.timeline.diff.TimelineChangeOperation> addOps = diff(empty, added);
        assertEquals(1, addOps.size());
        assertEquals(TimelineChangeType.RELATIONSHIP_ADDED, addOps.get(0).type());
        TimelinePatchApplicationResult addResult = apply(empty, addOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, addResult.status());
        assertEquals(1, addResult.patchedSnapshot().semanticRelationships().size());

        // MODIFY (member added) → GROUP_MEMBER_ADDED
        CanonicalTimelineSnapshot modified = withRels(empty, List.of(group("g1", "c1", "c2", "c3")));
        List<com.example.platform.timeline.diff.TimelineChangeOperation> modOps = diff(added, modified);
        assertEquals(1, modOps.size());
        assertEquals(TimelineChangeType.GROUP_MEMBER_ADDED, modOps.get(0).type());
        TimelinePatchApplicationResult modResult = apply(added, modOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, modResult.status());
        GroupRelationship g = (GroupRelationship) modResult.patchedSnapshot()
                .semanticRelationships().get(0);
        assertEquals(3, g.members().size());

        // IDENTICAL → no ops
        assertEquals(0, diff(modified, withRels(empty, List.of(group("g1", "c1", "c2", "c3")))).size());

        // DELETE
        List<com.example.platform.timeline.diff.TimelineChangeOperation> delOps = diff(modified, empty);
        assertEquals(1, delOps.size());
        assertEquals(TimelineChangeType.RELATIONSHIP_REMOVED, delOps.get(0).type());
        TimelinePatchApplicationResult delResult = apply(modified, delOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, delResult.status());
        assertEquals(0, delResult.patchedSnapshot().semanticRelationships().size());
    }

    @Test
    void syncAnchorChangeSingleOpViaProductionPath() {
        CanonicalTimelineSnapshot base = snap(List.of(sync("c1", "c2", 10)), "r0");
        CanonicalTimelineSnapshot modified = withRels(base, List.of(sync("c1", "c2", 25)));

        List<com.example.platform.timeline.diff.TimelineChangeOperation> ops = diff(base, modified);
        // R4-A3: anchor change is ONE typed op (SYNC_ANCHOR_CHANGED), never remove+add
        assertEquals(1, ops.size(), "sync anchor change must be a single typed op");
        assertEquals(TimelineChangeType.SYNC_ANCHOR_CHANGED, ops.get(0).type());
        TimelinePatchApplicationResult result = apply(base, ops);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        SyncRelationship after = (SyncRelationship) result.patchedSnapshot()
                .semanticRelationships().get(0);
        assertEquals(MediaTime.ofTicks(25, 1), after.localAnchorA(),
                "sync anchor edit must survive via the single typed op");
    }

    @Test
    void relationshipDivergentAndDeleteVsModify() {
        CanonicalTimelineSnapshot base = snap(List.of(group("g1", "c1", "c2")), "r0");
        CanonicalTimelineSnapshot ours = withRels(base, List.of(group("g1", "c1", "c2", "c3")));
        CanonicalTimelineSnapshot theirs = withRels(base, List.of(group("g1", "c1", "c2", "c4")));
        List<com.example.platform.timeline.diff.TimelineChangeOperation> oursOps = diff(base, ours);
        List<com.example.platform.timeline.diff.TimelineChangeOperation> theirsOps = diff(base, theirs);
        assertTrue(oursOps.stream().anyMatch(o -> o.type() == TimelineChangeType.GROUP_MEMBER_ADDED));
        assertTrue(theirsOps.stream().anyMatch(o -> o.type() == TimelineChangeType.GROUP_MEMBER_ADDED));
        assertEquals(oursOps.get(0).path().value(), theirsOps.get(0).path().value(),
                "same identity divergent edits target the same path → conflict candidate");

        // DELETE vs MODIFY
        CanonicalTimelineSnapshot deleted = snap(List.of(), "r0");
        List<com.example.platform.timeline.diff.TimelineChangeOperation> delOps = diff(base, deleted);
        assertTrue(delOps.stream().anyMatch(o -> o.type() == TimelineChangeType.RELATIONSHIP_REMOVED));
        List<com.example.platform.timeline.diff.TimelineChangeOperation> modOps = diff(base, ours);
        assertTrue(modOps.stream().anyMatch(o -> o.type() == TimelineChangeType.GROUP_MEMBER_ADDED));
        // different op types on the same relationship identity → deterministic conflict
        assertNotEquals(delOps.get(0).type(), modOps.get(0).type());
    }
}
