package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * C1-CNM1-RED-04/05/06/13 behavioral proofs over the sole canonical payload.
 *
 * <p>RED-04: exact fractional-rate timing survives canonical serialize -> merge ->
 * deserialize (24000/1001, 30000/1001, 60000/1001). RED-05: repeated merge drift
 * is zero. RED-06: existing clip effects survive merge reconstruction and absence
 * stays absent. RED-13: clip identity remains distinct from media-asset identity.
 */
class C1Cnm1RedBehavioralTest {

    private static final String PROJECT = "proj-cnm1-red";
    private static final String TENANT = "tenant-cnm1-red";
    private static final String BASE = "rev-base";
    private static final String SOURCE = "rev-source";
    private static final String TARGET = "rev-target";

    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private TimelineMergeEngine engine;

    @BeforeEach
    void setUp() {
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        TimelineMergePreviewService previewService = new TimelineMergePreviewService(
                new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector());
        engine = new TimelineMergeEngine(
                revisionRepository,
                snapshotService,
                mock(TimelineRevisionSaveService.class),
                previewService,
                new TimelineNonConflictingMergePlanner(previewService),
                new TimelinePatchApplier(),
                TimelineDocumentJsonSerializer.mapper(),
                mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                mock(com.example.platform.artifact.app.ArtifactPinService.class),
                mock(org.jooq.DSLContext.class));
    }

    private static TimelineClip clip(
            String clipId,
            String mediaAssetId,
            long startFrame,
            long durationFrames,
            FrameRate rate,
            boolean withEffect) {
        long numerator = rate.numerator().longValueExact();
        MediaTime start = MediaTime.ofFrames(startFrame, numerator, rate.denominator());
        MediaTime duration = MediaTime.ofFrames(durationFrames, numerator, rate.denominator());
        List<TimelineClipEffect> effects = withEffect
                ? List.of(new TimelineClipEffect("fx-1", "blur", Map.of("radius", 5)))
                : List.of();
        return new TimelineClip(
                clipId,
                mediaAssetId,
                null,
                null,
                null,
                start,
                start.add(duration),
                MediaTime.ZERO,
                duration,
                "MEDIA_STREAM",
                null,
                effects);
    }

    private static TimelineDocument document(TimelineClip primaryClip, boolean extraTrack) {
        List<TimelineTrack> tracks = new ArrayList<>();
        tracks.add(new TimelineTrack("v1", "Video 1", TrackType.VIDEO, List.of(primaryClip)));
        if (extraTrack) {
            tracks.add(new TimelineTrack(
                    "v2",
                    "Video 2",
                    TrackType.VIDEO,
                    List.of(clip("c2", "ast-2", 0, 5, FrameRate.of(30, 1), false))));
        }
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                tracks,
                new TimelineMetadata("CNM1", "canonical merge fixture", Map.of()));
    }

    private static TimelineDocument withExtraTrack(TimelineDocument document, int round) {
        List<TimelineTrack> tracks = new ArrayList<>(document.getTracks());
        tracks.add(new TimelineTrack(
                "v" + (2 + round),
                "Video " + (2 + round),
                TrackType.VIDEO,
                List.of(clip(
                        "c" + (10 + round),
                        "ast-" + (10 + round),
                        0,
                        5,
                        FrameRate.of(30, 1),
                        false))));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                tracks,
                document.getMetadata(),
                document.getAudioMix(),
                document.getSemanticRelationships(),
                document.getTextElements(),
                document.getTransitions(),
                document.getAutomations());
    }

    private void stubDocuments(
            TimelineDocument base, TimelineDocument source, TimelineDocument target) {
        stubRevision(BASE, "snap-base", base);
        stubRevision(SOURCE, "snap-source", source);
        stubRevision(TARGET, "snap-target", target);
    }

    private void stubRevision(
            String revisionId, String snapshotId, TimelineDocument document) {
        when(revisionRepository.findOwnedById(revisionId, PROJECT, TENANT))
                .thenReturn(Optional.of(new TimelineRevisionRepository.RevisionRow(
                        revisionId,
                        PROJECT,
                        TENANT,
                        null,
                        1,
                        snapshotId,
                        0,
                        new com.example.platform.timeline.canonical.TimelineContentDigester()
                                .digest(document),
                        TimelineDocument.CURRENT_SCHEMA_VERSION,
                        "merge-test",
                        "server-user",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        OffsetDateTime.now())));
        when(snapshotService.findOwnedById(PROJECT, TENANT, snapshotId))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo(
                        snapshotId,
                        PROJECT,
                        TENANT,
                        TimelineDocumentJsonSerializer.serialize(document),
                        TimelineDocument.CURRENT_SCHEMA_VERSION)));
    }

    private static TimelineMergeRequest request(String message) {
        return new TimelineMergeRequest(
                PROJECT, TENANT, BASE, SOURCE, TARGET, "server-user", message);
    }

    private static TimelineDocument mergedDocument(TimelineMergeResult result) {
        assertEquals(
                TimelineMergeResult.MergeStatus.MERGED,
                result.status(),
                String.valueOf(result.summary()));
        TimelineDocument document = TimelineDocumentJsonSerializer.deserialize(
                result.mergedPayloadJson());
        assertEquals(TimelineDocument.CURRENT_SCHEMA_VERSION, document.getSchemaVersion());
        return document;
    }

    private static TimelineClip firstClip(TimelineDocument document) {
        return document.getTracks().getFirst().clips().getFirst();
    }

    /**
     * The canonical document stores exact rational clip time. Given the known frame
     * count, its exact duration reconstructs the authored FrameRate without a floating
     * projection: frames / seconds = frames * timeScale / ticks.
     */
    private static FrameRate frameRateFromDuration(TimelineClip clip, long durationFrames) {
        MediaTime duration = clip.getEndTime().subtract(clip.getStartTime());
        return FrameRate.of(
                Math.multiplyExact(durationFrames, duration.timeScale()),
                duration.ticks());
    }

    @Test
    void fractionalRateDenominatorSurvivesMerge() {
        for (FrameRate rate : List.of(
                FrameRate.of(24_000, 1_001),
                FrameRate.of(30_000, 1_001),
                FrameRate.of(60_000, 1_001))) {
            TimelineDocument base = document(clip("c1", "ast-1", 30, 10, rate, false), false);
            TimelineDocument source = document(clip("c1", "ast-1", 30, 10, rate, false), true);
            stubDocuments(base, source, base);

            TimelineClip merged = firstClip(mergedDocument(
                    engine.mergeSemantic(request("fr-" + rate.numerator()))));

            assertEquals(
                    MediaTime.ofFrames(30, rate.numerator().longValueExact(), rate.denominator()),
                    merged.getStartTime(),
                    "exact canonical start must survive @ " + rate);
            FrameRate recovered = frameRateFromDuration(merged, 10);
            assertEquals(rate, recovered, "exact fractional rate must survive @ " + rate);
            assertEquals(1_001L, recovered.denominator(), "fractional denominator must survive");
        }
    }

    @Test
    void repeatedMergeDriftIsZeroAtFractionalRate() {
        FrameRate expectedRate = FrameRate.of(30_000, 1_001);
        MediaTime expectedStart = MediaTime.ofFrames(30, 30_000, 1_001);
        TimelineDocument current = document(
                clip("c1", "ast-1", 30, 10, expectedRate, false), false);

        for (int round = 0; round < 3; round++) {
            TimelineDocument source = withExtraTrack(current, round);
            stubDocuments(current, source, current);
            current = mergedDocument(engine.mergeSemantic(request("rep-" + round)));

            TimelineClip unchanged = firstClip(current);
            assertEquals(expectedStart, unchanged.getStartTime(), "time drift at round " + round);
            assertEquals(
                    expectedRate,
                    frameRateFromDuration(unchanged, 10),
                    "fractional-rate drift at round " + round);
            assertEquals(1_001L, frameRateFromDuration(unchanged, 10).denominator());
        }
    }

    @Test
    void clipEffectsSurviveMergeReconstruction() {
        TimelineDocument base = document(
                clip("c1", "ast-1", 0, 10, FrameRate.of(30, 1), true), false);
        TimelineDocument source = document(
                clip("c1", "ast-1", 0, 10, FrameRate.of(30, 1), true), true);
        stubDocuments(base, source, base);

        TimelineClip merged = firstClip(mergedDocument(
                engine.mergeSemantic(request("fx-preserve"))));

        assertEquals(1, merged.getEffects().size(), "effect must survive merge reconstruction");
        assertEquals("fx-1", merged.getEffects().getFirst().id());
        assertEquals("blur", merged.getEffects().getFirst().effectKey());
        assertEquals(5, merged.getEffects().getFirst().parameters().get("radius"));
    }

    @Test
    void effectAbsenceRemainsAbsence() {
        TimelineDocument base = document(
                clip("c1", "ast-1", 0, 10, FrameRate.of(30, 1), false), false);
        TimelineDocument source = document(
                clip("c1", "ast-1", 0, 10, FrameRate.of(30, 1), false), true);
        stubDocuments(base, source, base);

        TimelineClip merged = firstClip(mergedDocument(
                engine.mergeSemantic(request("fx-absent"))));

        assertTrue(merged.getEffects().isEmpty(), "effect absence must remain absence");
    }

    @Test
    void clipIdentityAndAssetIdentityRemainDistinct() {
        TimelineDocument base = document(
                clip("clip_001", "ast_smoke_001", 0, 10, FrameRate.of(30, 1), true), false);
        TimelineDocument source = document(
                clip("clip_001", "ast_smoke_001", 0, 10, FrameRate.of(30, 1), true), true);
        stubDocuments(base, source, base);

        TimelineClip merged = firstClip(mergedDocument(
                engine.mergeSemantic(request("identity"))));

        assertEquals("clip_001", merged.getClipId().value(), "clip identity must be preserved");
        assertEquals(
                "ast_smoke_001",
                merged.getMediaAssetId(),
                "media-asset identity must be preserved");
        assertNotEquals(merged.getClipId().value(), merged.getMediaAssetId());
    }
}
