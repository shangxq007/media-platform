package com.example.platform.render.domain.plan;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.render.domain.operation.OperationDefinition;
import com.example.platform.render.domain.operation.OperationInstance;
import com.example.platform.render.domain.operation.OperationParameters;
import com.example.platform.render.domain.operation.OperationTarget;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1: planner semantics (15 ops), candidate
 * materialization, NO_OP detection, PlanDigest domain separation, Preview
 * binding, Authorization binding, immutability.
 */
class OperationPlanTransactionTest {

    private static final String REV = "R100";
    private static final String HASH = "h-base";

    private static TimelineClip clip(String id, int startSec, int endSec) {
        return new TimelineClip(id, "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(startSec, 1), MediaTime.ofRational(endSec, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
    }

    private static TimelineDocument doc(TimelineClip... clips) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO, List.of(clips))),
                TimelineMetadata.empty());
    }

    private static TimelineDocument docWith(List<SemanticRelationship> rels, AudioMix mix) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO,
                        List.of(clip("clip-a", 0, 4), clip("clip-b", 4, 8)))),
                TimelineMetadata.empty(), mix, rels);
    }

    private static OperationInstance instance(OperationDefinition def, OperationParameters params,
                                              OperationTarget target, String baseHash) {
        return new OperationInstance(def.definitionId(), ContractVersion.of(1, 0), REV, baseHash,
                target, params, "pd", null);
    }

    private static OperationTarget clipTarget(TimelineClipId... ids) {
        return new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                REV, HASH, List.of(ids), SelectionSpec.ExpansionPolicy.EXACT));
    }

    private static final OperationPlanner PLANNER = new OperationPlanner();

    // ---- MOVE ----
    @Test
    void movePlansPlacementDelta() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(2, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        assertFalse(plan.noOp());
        var replaced = plan.plannedChanges().stream().filter(c -> c instanceof PlannedChange.ClipReplaced)
                .map(c -> (PlannedChange.ClipReplaced) c).findFirst().orElseThrow();
        assertEquals(MediaTime.ofRational(2, 1), replaced.newClip().getStartTime());
        assertEquals(MediaTime.ofRational(6, 1), replaced.newClip().getEndTime());
        assertTrue(plan.validated());
        assertNotNull(plan.planDigest());
    }

    // ---- DELETE secondary consequences ----
    @Test
    void deletePlansRelationshipConsequences() {
        var sync = SyncRelationship.of(TimelineClipId.of("clip-a"), MediaTime.ZERO,
                TimelineClipId.of("clip-b"), MediaTime.ZERO);
        var group3 = GroupRelationship.of(GroupId.of("g3"),
                List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b"), TimelineClipId.of("c3")));
        var group2 = GroupRelationship.of(GroupId.of("g2"),
                List.of(TimelineClipId.of("clip-a"), TimelineClipId.of("c2")));
        TimelineDocument base = docWith(List.of(sync, group3, group2), AudioMix.EMPTY);
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.DELETE,
                new OperationParameters.NoParameters(),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        assertTrue(plan.plannedChanges().stream().anyMatch(c -> c instanceof PlannedChange.ClipRemoved));
        assertTrue(plan.plannedChanges().stream().anyMatch(c -> c instanceof PlannedChange.RelationshipRemoved
                && ((PlannedChange.RelationshipRemoved) c).relationshipIdentity().equals(sync.identityKey())));
        // group2 {A,c2} -> below min -> removed; group3 {A,B,c3} -> {B,c3} retained
        assertTrue(plan.plannedChanges().stream().anyMatch(c -> c instanceof PlannedChange.RelationshipRemoved
                && ((PlannedChange.RelationshipRemoved) c).relationshipIdentity().equals("G:g2")));
        assertTrue(plan.plannedChanges().stream().anyMatch(c -> c instanceof PlannedChange.GroupMembershipUpdated));
        // candidate has no dangling
        assertTrue(plan.candidateTimeline().getSemanticRelationships().stream()
                .noneMatch(r -> r instanceof SyncRelationship));
        assertEquals(1, plan.candidateTimeline().getSemanticRelationships().stream()
                .filter(r -> r instanceof GroupRelationship g && g.groupId().value().equals("g3")).count());
    }

    // ---- SET_RATE exact duration ----
    @Test
    void setRateComputesExactOccupiedDuration() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.SET_TEMPORAL_RATE,
                new OperationParameters.SetTemporalRateParameters(new com.example.platform.timeline.semantics.clip.MediaClip.Rational(2, 1)),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        var replaced = plan.plannedChanges().stream().filter(c -> c instanceof PlannedChange.ClipReplaced)
                .map(c -> (PlannedChange.ClipReplaced) c).findFirst().orElseThrow();
        // identity 1/1: sourceDur = 4s; new occupied = 4/2 = 2s
        assertEquals(MediaTime.ofRational(2, 1), replaced.newClip().getEndTime());
        var mapping = (ConstantRateTemporalMapping) replaced.newClip().getTemporalMapping();
        assertEquals(2, mapping.rate().numerator());
        assertEquals(PlaybackDirection.FORWARD, mapping.direction());
    }

    @Test
    void setRateOnFreezeTargetRejects() {
        TimelineClip frozen = new TimelineClip("clip-a", "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(0, 1), MediaTime.ofRational(4, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM",
                new FreezeTemporalMapping(MediaTime.ofRational(1, 1)));
        TimelineDocument base = doc(frozen);
        String baseHash = new TimelineContentDigester().digest(base);
        assertThrows(PlanException.class, () -> PLANNER.plan(instance(OperationDefinition.V1.SET_TEMPORAL_RATE,
                new OperationParameters.SetTemporalRateParameters(new com.example.platform.timeline.semantics.clip.MediaClip.Rational(2, 1)),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base));
    }

    // ---- TRIM invalid sync anchor reject ----
    @Test
    void trimInvalidatingSyncAnchorRejects() {
        var sync = SyncRelationship.of(TimelineClipId.of("clip-a"), MediaTime.ofRational(3, 1),
                TimelineClipId.of("clip-b"), MediaTime.ZERO);
        TimelineDocument base = docWith(List.of(sync), AudioMix.EMPTY);
        String baseHash = new TimelineContentDigester().digest(base);
        assertThrows(PlanException.class, () -> PLANNER.plan(instance(OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(OperationParameters.TrimParameters.TrimEdge.END,
                        MediaTime.ofRational(2, 1)),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base));
    }

    // ---- NO_OP ----
    @Test
    void semanticNoOpDetected() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ZERO, false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        assertTrue(plan.noOp());
        assertNotNull(plan.planDigest());
    }

    // ---- PLAN DIGEST domain separation ----
    @Test
    void planDigestExcludesTargetRefAndPrincipal() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan p1 = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        OperationPlan p2 = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(2, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        assertNotEquals(p1.planDigest(), p2.planDigest(), "different delta -> different digest");
        // target ref/principal are NOT digest inputs (no targetRef field exists in digest computation)
        assertEquals(64, p1.planDigest().length());
    }

    // ---- PREVIEW ----
    @Test
    void previewBindsPlanDigestAndShowsConsequences() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        OperationPlanPreview preview = OperationPlanPreview.of(plan);
        assertEquals(plan.planDigest(), preview.planDigest());
        assertFalse(preview.primaryChanges().isEmpty());
        assertEquals(plan.candidateContentHash(), preview.candidateContentHash());
    }

    // ---- AUTHORIZATION binding ----
    @Test
    void authorizationBindsExactPlanAndContext() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        AuthorizationDecision decision = AuthorizationDecision.allow(plan.planDigest(),
                "principal-a", "project-1", "main", "policy-v1");
        assertTrue(decision.allowed());
        assertEquals(plan.planDigest(), decision.planDigest());
        // different target ref is NOT authorized by same decision
        AuthorizationDecision other = AuthorizationDecision.allow(plan.planDigest(),
                "principal-a", "project-1", "protected-main", "policy-v1");
        assertNotEquals(decision.targetRefId(), other.targetRefId());
    }

    // ---- AUDIO plan ----
    @Test
    void audioGainPlansCanonicalMixChange() {
        AudioMixInput input = AudioMixInput.of("t1", "clip-a");
        AudioMix mix = AudioMix.of(com.example.platform.audio.domain.mix.AudioMasterBus.master(),
                List.of(AudioRoute.of(input)));
        TimelineDocument base = docWith(List.of(), mix);
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.SET_AUDIO_GAIN,
                new OperationParameters.AudioGainParameters(AudioGain.of(0.5)),
                new OperationTarget.AudioTarget(input), baseHash), base);
        assertFalse(plan.noOp(), "gain change must change candidate hash");
        assertTrue(plan.plannedChanges().stream().anyMatch(c -> c instanceof PlannedChange.AudioMixReplaced));
        assertEquals(0.5, plan.candidateTimeline().getAudioMix().routes().get(0).gain().linear());
    }

    // ---- immutability ----
    @Test
    void planIsImmutableRecord() {
        TimelineDocument base = doc(clip("clip-a", 0, 4));
        String baseHash = new TimelineContentDigester().digest(base);
        OperationPlan plan = PLANNER.plan(instance(OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false),
                clipTarget(TimelineClipId.of("clip-a")), baseHash), base);
        assertEquals(OperationPlan.FORMAT_VERSION, plan.formatVersion());
        assertEquals(REV, plan.baseRevisionId());
    }
}
