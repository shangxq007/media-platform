package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.testsupport.CanonicalFieldsAssert;
import com.example.platform.operation.testsupport.CanonicalFieldsAssert.Field;
import com.example.platform.operation.testsupport.RichTimelineDocument;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIELD-PRESERVATION-P0 (Sink 2 + Sink 3): {@link OperationPlanner} rebuilds the whole
 * document for every clip/relationship/audio operation. Each candidate must preserve all
 * canonical fields it does not author — including {@code metadata}, which the pre-fix
 * {@code rebuild} reset to {@code TimelineMetadata.empty()}.
 *
 * <p>Every test drives the production planner (not a sibling type) on a base whose 8
 * canonical fields are all non-empty, and asserts the untouched 7 are preserved.
 */
class OperationPlannerFieldPreservationTest {

    private static final String REV = RichTimelineDocument.BASE_REVISION_ID;
    private static final String HASH = RichTimelineDocument.BASE_CONTENT_HASH;

    // ---- Sink 2: rebuild call sites ----

    @Test
    void moveReplaceClipRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.MOVE, clipTarget("clip-3"),
                new OperationParameters.MoveParameters(MediaTime.ofMillis(500), false));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TRACKS);
        assertNotEquals(base.getTracks(), candidate.getTracks(), "move authors tracks");
    }

    @Test
    void multiMoveNamesUnsupportedTransitionAndAutomationBindings() {
        var base = RichTimelineDocument.rich();
        var target = new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                REV, HASH, List.of(TimelineClipId.of("clip-1"), TimelineClipId.of("clip-2")),
                SelectionSpec.ExpansionPolicy.EXACT));
        var move = instance(OperationDefinition.V1.MOVE, target,
                new OperationParameters.MoveParameters(MediaTime.ofMillis(500), false));
        var transition = assertThrows(PlanException.class, () -> plan(move, base));
        assertTrue(transition.getMessage().contains("transition-1"), transition.getMessage());
        var withoutTransition = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), base.getSemanticRelationships(), base.getTextElements(), List.of(), base.getAutomations());
        var automation = assertThrows(PlanException.class, () -> plan(move, withoutTransition));
        assertTrue(automation.getMessage().contains("automation binding for clip-1"), automation.getMessage());
    }

    @Test
    void trimStartConsumesTheMatchingSourceRange() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.TRIM, clipTarget("clip-3"),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.START, MediaTime.ofMillis(250)));

        TimelineDocument candidate = plan(inst, base);
        var clip = candidate.getTracks().getFirst().clips().get(2);
        assertEquals(MediaTime.ofMillis(2250), clip.getStartTime());
        assertEquals(MediaTime.ofMillis(3000), clip.getEndTime());
        assertEquals(MediaTime.ofMillis(250), clip.getTrimStart());
        assertEquals(MediaTime.ofMillis(1000), clip.getTrimEnd());
        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TRACKS);
    }

    @Test
    void trimCannotConsumeTheEntireClip() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.TRIM, clipTarget("clip-3"),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofMillis(1000)));

        assertThrows(PlanException.class, () -> plan(inst, base));
    }

    @Test
    void trimBeyondSourceEndIsRejectedAsInvalidPlan() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.TRIM, clipTarget("clip-3"),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofMillis(2000)));
        PlanException rejected = assertThrows(PlanException.class, () -> plan(inst, base));
        assertEquals(PlanErrorCode.INVALID_PLAN, rejected.code());
    }

    @Test
    void moveAndTrimPreserveAuthoredClipEffects() {
        TimelineDocument rich = RichTimelineDocument.rich();
        TimelineTrack track = rich.getTracks().getFirst();
        var original = track.clips().get(2);
        var effect = new TimelineClipEffect("fx-1", "color", Map.of("strength", 2));
        var decorated = new com.example.platform.timeline.canonical.TimelineClip(
                original.getClipId().value(), original.getMediaAssetId(),
                original.getMediaStreamId(), original.getArtifactId(), original.getContentDigest(),
                original.getStartTime(), original.getEndTime(), original.getTrimStart(),
                original.getTrimEnd(), original.getSourceKind(), original.getTemporalMapping(),
                List.of(effect));
        TimelineDocument base = new TimelineDocument(rich.getSchemaVersion(),
                List.of(new TimelineTrack(track.trackId(), track.name(), track.type(),
                        List.of(track.clips().get(0), track.clips().get(1), decorated))),
                rich.getMetadata(), rich.getAudioMix(), rich.getSemanticRelationships(),
                rich.getTextElements(), rich.getTransitions(), rich.getAutomations());

        var moved = plan(instance(OperationDefinition.V1.MOVE, clipTarget("clip-3"),
                new OperationParameters.MoveParameters(MediaTime.ofMillis(500), false)), base);
        var trimmed = plan(instance(OperationDefinition.V1.TRIM, clipTarget("clip-3"),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofMillis(250))), base);

        assertEquals(List.of(effect), moved.getTracks().getFirst().clips().get(2).getEffects());
        assertEquals(List.of(effect), trimmed.getTracks().getFirst().clips().get(2).getEffects());
    }

    @Test
    void reverseFractionalRateTrimConsumesTheCorrectSourceEdge() {
        TimelineDocument rich = RichTimelineDocument.rich();
        TimelineTrack track = rich.getTracks().getFirst();
        var original = track.clips().get(2);
        var reverse = new com.example.platform.timeline.canonical.TimelineClip(
                original.getClipId().value(), original.getMediaAssetId(),
                original.getMediaStreamId(), original.getArtifactId(), original.getContentDigest(),
                original.getStartTime(), original.getEndTime(), MediaTime.ZERO,
                MediaTime.ofMillis(1500), original.getSourceKind(),
                ConstantRateTemporalMapping.of(3, 2, PlaybackDirection.REVERSE));
        TimelineDocument base = new TimelineDocument(rich.getSchemaVersion(),
                List.of(new TimelineTrack(track.trackId(), track.name(), track.type(),
                        List.of(track.clips().get(0), track.clips().get(1), reverse))),
                rich.getMetadata(), rich.getAudioMix(), rich.getSemanticRelationships(),
                rich.getTextElements(), rich.getTransitions(), rich.getAutomations());

        var candidate = plan(instance(OperationDefinition.V1.TRIM, clipTarget("clip-3"),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.START, MediaTime.ofMillis(200))), base);
        var clip = candidate.getTracks().getFirst().clips().get(2);
        assertEquals(MediaTime.ofMillis(2200), clip.getStartTime());
        assertEquals(MediaTime.ZERO, clip.getTrimStart());
        assertEquals(MediaTime.ofMillis(1200), clip.getTrimEnd());
    }

    @Test
    void deleteRemoveClipRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        // clip-3 is not referenced by any relationship, so only tracks change.
        OperationInstance inst = instance(OperationDefinition.V1.DELETE, clipTarget("clip-3"),
                new OperationParameters.NoParameters());

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.TRACKS);
        assertNotEquals(base.getTracks(), candidate.getTracks(), "delete authors tracks");
    }

    @Test
    void audioGainRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.SET_AUDIO_GAIN,
                new OperationTarget.AudioTarget(AudioMixInput.of("track-1", "clip-1")),
                new OperationParameters.AudioGainParameters(AudioGain.of(0.5)));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.AUDIO_MIX);
        assertNotEquals(base.getAudioMix(), candidate.getAudioMix(), "audio op authors mix");
    }

    @Test
    void groupCreateRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.CREATE_GROUP, timelineTarget(),
                new OperationParameters.CreateGroupParameters(GroupId.of("g-2"),
                        List.of(TimelineClipId.of("clip-2"), TimelineClipId.of("clip-3"))));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
        assertNotEquals(base.getSemanticRelationships(), candidate.getSemanticRelationships(),
                "group create authors relationships");
    }

    @Test
    void groupUpdateMembersRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.UPDATE_GROUP_MEMBERSHIP,
                new OperationTarget.GroupTarget(GroupId.of("g-1")),
                new OperationParameters.UpdateGroupMembershipParameters(
                        Set.of(TimelineClipId.of("clip-3")), Set.of()));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    @Test
    void groupRemoveRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.REMOVE_GROUP,
                new OperationTarget.GroupTarget(GroupId.of("g-1")),
                new OperationParameters.NoParameters());

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    @Test
    void syncCreateRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.CREATE_SYNC, timelineTarget(),
                new OperationParameters.CreateSyncParameters(TimelineClipId.of("clip-2"), MediaTime.ZERO,
                        TimelineClipId.of("clip-3"), MediaTime.ZERO));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    @Test
    void syncUpdateAnchorRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.UPDATE_SYNC_ANCHOR,
                new OperationTarget.SyncTarget(RichTimelineDocument.baseSync().identityKey()),
                new OperationParameters.UpdateSyncAnchorParameters(
                        MediaTime.ofMillis(500), MediaTime.ofMillis(500)));

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    @Test
    void syncRemoveRebuildPreservesUntouchedFields() {
        TimelineDocument base = RichTimelineDocument.rich();
        OperationInstance inst = instance(OperationDefinition.V1.REMOVE_SYNC,
                new OperationTarget.SyncTarget(RichTimelineDocument.baseSync().identityKey()),
                new OperationParameters.NoParameters());

        TimelineDocument candidate = plan(inst, base);

        CanonicalFieldsAssert.assertPreserved(base, candidate, Field.SEMANTIC_RELATIONSHIPS);
    }

    private static TimelineDocument plan(OperationInstance inst, TimelineDocument base) {
        return new OperationPlanner().plan(inst, REV, base).candidateTimeline();
    }

    private static OperationInstance instance(OperationDefinition def, OperationTarget target,
                                              OperationParameters params) {
        return new OperationInstance(def.definitionId(), def.version(), REV, HASH,
                target, params, "param-digest", null);
    }

    private static OperationTarget timelineTarget() {
        return new OperationTarget.TimelineTarget("timeline-T");
    }

    private static OperationTarget.ResolvedClipScopeTarget clipTarget(String clipId) {
        return new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(
                REV, HASH, List.of(TimelineClipId.of(clipId)), SelectionSpec.ExpansionPolicy.EXACT));
    }
}
