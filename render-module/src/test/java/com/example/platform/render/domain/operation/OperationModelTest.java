package com.example.platform.render.domain.operation;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.semantics.relationship.GroupId;
import com.example.platform.render.domain.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.render.domain.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.render.domain.timeline.semantics.selection.SelectionSpec;
import com.example.platform.render.domain.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OPERATION_MODEL_FOUNDATION_V1: definitions, target contracts, request->
 * resolve->instance, base binding, single-authority temporal parameters,
 * parameter digest, batch, typed errors, Timeline hash non-participation.
 */
class OperationModelTest {

    private static final String REV = "R100";
    private static final String HASH = "hash-100";

    private static TimelineClip clip(String id) {
        return new TimelineClip(id, "asset-1", "stream-1", "artifact-1", "digest-1",
                MediaTime.ofRational(0, 1), MediaTime.ofRational(3, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
    }

    private static TimelineDocument doc() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO,
                        List.of(clip("clip-a"), clip("clip-b"), clip("clip-c")))),
                TimelineMetadata.empty());
    }

    private static OperationRequestResolver.OperationBaseContext base() {
        return new OperationRequestResolver.OperationBaseContext(REV, HASH, doc());
    }

    private static OperationRequest req(OperationDefinition def, OperationTargetRequest target,
                                        OperationParameters params) {
        return new OperationRequest(def.definitionId(), def.version(), target, params, REV, HASH, null);
    }

    private static OperationTargetRequest.ClipSelectionTargetRequest clipTarget(TimelineClipId... ids) {
        return new OperationTargetRequest.ClipSelectionTargetRequest(
                new SelectionSpec.ExplicitObjectSelection(List.of(ids)), SelectionSpec.ExpansionPolicy.EXACT);
    }

    // ---- DEFINITIONS ----
    @Test
    void exactlyFifteenV1Definitions() {
        assertEquals(15, OperationDefinition.V1.ALL.size());
        assertTrue(OperationDefinition.V1.ALL.stream().allMatch(d ->
                d.version().equals(ContractVersion.of(1, 0)) && d.lifecycle() == OperationDefinition.Lifecycle.ACTIVE));
        assertEquals("timeline.move", OperationDefinition.V1.MOVE.definitionId().value());
        assertEquals("audio.gain.set", OperationDefinition.V1.SET_AUDIO_GAIN.definitionId().value());
        assertEquals("timeline.sync.update-anchor", OperationDefinition.V1.UPDATE_SYNC_ANCHOR.definitionId().value());
    }

    // ---- TARGET CONTRACTS ----
    @Test
    void trimCardinalityExactlyOne() {
        assertEquals(1, OperationDefinition.V1.TRIM.minCardinality());
        assertEquals(1, OperationDefinition.V1.TRIM.maxCardinality());
    }

    @Test
    void freezeCardinalityExactlyOne() {
        assertEquals(1, OperationDefinition.V1.FREEZE.maxCardinality());
    }

    @Test
    void createGroupCardinalityAtLeastTwo() {
        assertEquals(2, OperationDefinition.V1.CREATE_GROUP.minCardinality());
    }

    // ---- RESOLUTION / BASE BINDING ----
    @Test
    void requestResolvesToRevisionBoundInstance() throws Exception {
        OperationInstance inst = OperationRequestResolver.resolve(
                req(OperationDefinition.V1.SET_TEMPORAL_RATE, clipTarget(TimelineClipId.of("clip-a")),
                        new OperationParameters.SetTemporalRateParameters(new com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational(2, 1))),
                base());
        assertEquals(REV, inst.baseRevisionId());
        assertEquals(HASH, inst.baseContentHash());
        assertTrue(inst.target() instanceof OperationTarget.ResolvedClipScopeTarget);
        assertNotNull(inst.parameterDigest());
    }

    @Test
    void baseMismatchFailsClosed() {
        OperationRequest request = req(OperationDefinition.V1.DELETE, clipTarget(TimelineClipId.of("clip-a")),
                new OperationParameters.NoParameters());
        var wrongBase = new OperationRequestResolver.OperationBaseContext("R101", "hash-101", doc());
        OperationResolutionException ex = assertThrows(OperationResolutionException.class,
                () -> OperationRequestResolver.resolve(request, wrongBase));
        assertEquals(OperationErrorCode.STALE_BASE_REVISION, ex.code());
    }

    @Test
    void groupTargetNeedsNoFakeSelectionSpec() throws Exception {
        var withGroup = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "main", TrackType.VIDEO,
                        List.of(clip("clip-a"), clip("clip-b")))),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(
                        GroupRelationship.of(GroupId.of("g1"), List.of(
                                TimelineClipId.of("clip-a"), TimelineClipId.of("clip-b")))));
        OperationInstance inst = OperationRequestResolver.resolve(
                new OperationRequest(OperationDefinition.V1.REMOVE_GROUP.definitionId(),
                        OperationDefinition.V1.REMOVE_GROUP.version(),
                        new OperationTargetRequest.GroupTargetRequest(GroupId.of("g1")),
                        new OperationParameters.NoParameters(), REV, HASH, null),
                new OperationRequestResolver.OperationBaseContext(REV, HASH, withGroup));
        assertTrue(inst.target() instanceof OperationTarget.GroupTarget);
    }

    @Test
    void missingGroupTargetFailsClosed() {
        OperationResolutionException ex = assertThrows(OperationResolutionException.class,
                () -> OperationRequestResolver.resolve(
                        new OperationRequest(OperationDefinition.V1.REMOVE_GROUP.definitionId(),
                                OperationDefinition.V1.REMOVE_GROUP.version(),
                                new OperationTargetRequest.GroupTargetRequest(GroupId.of("nope")),
                                new OperationParameters.NoParameters(), REV, HASH, null),
                        base()));
        assertEquals(OperationErrorCode.INVALID_SCOPE, ex.code());
    }

    // ---- TEMPORAL SINGLE AUTHORITY ----
    @Test
    void setRateContainsOnlyRate() {
        var params = new OperationParameters.SetTemporalRateParameters(new com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational(2, 1));
        assertNotNull(params.rate());
    }

    @Test
    void setDirectionContainsOnlyDirection() {
        var params = new OperationParameters.SetTemporalDirectionParameters(PlaybackDirection.REVERSE);
        assertNotNull(params.direction());
    }

    @Test
    void freezeContainsOnlySourcePosition() {
        var params = new OperationParameters.FreezeParameters(MediaTime.ofRational(1, 1));
        assertNotNull(params.sourcePosition());
    }

    @Test
    void syncCreateRequiresDistinctEndpoints() {
        assertThrows(IllegalArgumentException.class, () ->
                new OperationParameters.CreateSyncParameters(TimelineClipId.of("clip-a"),
                        MediaTime.ZERO, TimelineClipId.of("clip-a"), MediaTime.ZERO));
    }

    @Test
    void groupMembershipContradictionRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new OperationParameters.UpdateGroupMembershipParameters(
                        Set.of(TimelineClipId.of("clip-a")), Set.of(TimelineClipId.of("clip-a"))));
    }

    // ---- PARAMETER DIGEST ----
    @Test
    void digestDeterministicAndDomainSeparated() {
        var r1 = new OperationParameters.SetTemporalRateParameters(new com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational(2, 1));
        var r2 = new OperationParameters.SetTemporalRateParameters(new com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational(4, 2));
        String d1 = ParameterDigest.compute(OperationDefinition.V1.SET_TEMPORAL_RATE.definitionId(),
                OperationDefinition.V1.SET_TEMPORAL_RATE.version(), r1);
        String d2 = ParameterDigest.compute(OperationDefinition.V1.SET_TEMPORAL_RATE.definitionId(),
                OperationDefinition.V1.SET_TEMPORAL_RATE.version(), r2);
        assertEquals(d1, d2, "normalized 2/1 == 4/2");
        String d3 = ParameterDigest.compute(OperationDefinition.V1.SET_TEMPORAL_RATE.definitionId(),
                OperationDefinition.V1.SET_TEMPORAL_RATE.version(),
                new OperationParameters.SetTemporalRateParameters(new com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational(3, 1)));
        assertNotEquals(d1, d3, "different rate -> different digest");
        // domain separation: same structural value under different definition
        String d4 = ParameterDigest.compute(OperationDefinition.V1.SET_TEMPORAL_DIRECTION.definitionId(),
                OperationDefinition.V1.SET_TEMPORAL_DIRECTION.version(),
                new OperationParameters.SetTemporalDirectionParameters(PlaybackDirection.FORWARD));
        assertNotEquals(d1, d4);
    }

    // ---- BATCH ----
    @Test
    void batchRequiresSingleBaseAndNonEmpty() {
        OperationInstance inst = new OperationInstance(OperationDefinition.V1.DELETE.definitionId(),
                ContractVersion.of(1, 0), REV, HASH,
                new OperationTarget.ResolvedClipScopeTarget(new com.example.platform.render.domain.timeline.semantics.selection.ResolvedScope(
                        REV, HASH, List.of(TimelineClipId.of("clip-a")), SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.NoParameters(), "digest", null);
        assertThrows(IllegalArgumentException.class, () -> new OperationBatch(List.of(), REV, HASH));
        OperationBatch batch = new OperationBatch(List.of(inst), REV, HASH);
        assertEquals(1, batch.instances().size());
        assertThrows(IllegalArgumentException.class, () -> new OperationBatch(List.of(inst), "R101", HASH));
    }

    // ---- TIMELINE HASH NON-PARTICIPATION ----
    @Test
    void operationResolutionDoesNotChangeTimelineHash() throws Exception {
        TimelineDocument d = doc();
        String before = new TimelineContentDigester().digest(d);
        OperationRequestResolver.resolve(
                req(OperationDefinition.V1.MOVE, clipTarget(TimelineClipId.of("clip-a")),
                        new OperationParameters.MoveParameters(MediaTime.ofRational(1, 1), false)),
                new OperationRequestResolver.OperationBaseContext(REV, HASH, d));
        String after = new TimelineContentDigester().digest(d);
        assertEquals(before, after, "operation resolution must not change Timeline hash");
    }
}
