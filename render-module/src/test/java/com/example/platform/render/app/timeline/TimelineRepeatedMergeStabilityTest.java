package com.example.platform.render.app.timeline;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.render.domain.timeline.internal.TimelineMergeRequest;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C1-CRR2 repeated-merge stability — production save -> merge -> persist ->
 * reload -> merge again, with NON-millisecond-aligned frames (the CRR1-FCV
 * drift surface: frame 1/2/4/5/7/8/10 @30fps).
 *
 * <p>Unedited time fields must be EXACTLY unchanged across repeated merges.
 * The C1-CRR1 candidate drifted (frame 2 -> frame 1 -> frame 0 over two
 * merges); with the paired half-up quantization this must not occur.</p>
 */
class TimelineRepeatedMergeStabilityTest {

    private static final String PROJECT = "proj-rm";
    private static final String TENANT = "tenant-rm";
    private static final int FPS = 30;

    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineMergeEngine engine;
    private ObjectMapper mapper = InternalTimelineJson.mapper();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        currentRevisionService = mock(ProductCurrentRevisionService.class);
        TimelineMergePreviewService previewService = new TimelineMergePreviewService(
                new com.example.platform.render.domain.timeline.diff.merge.TimelineMergeConflictDetector());
        TimelineNonConflictingMergePlanner planner = new TimelineNonConflictingMergePlanner(previewService);
        engine = new TimelineMergeEngine(revisionRepository, snapshotService, currentRevisionService,
                previewService, planner,
                new com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplier(),
                mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String payloadWithClipAtFrame(long frame, long durFrames) {
        return payloadWithClips(
                clip("c1", "ast-1", frame, durFrames),
                List.of());
    }

    private String payloadWithClips(ObjectNode primaryClip, List<ObjectNode> extraClips) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-rm");
        ObjectNode composition = mapper.createObjectNode();
        ArrayNode tracks = mapper.createArrayNode();
        ObjectNode track = mapper.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        ArrayNode clips = mapper.createArrayNode();
        if (primaryClip != null) {
            clips.add(primaryClip);
        }
        track.set("clips", clips);
        tracks.add(track);
        if (!extraClips.isEmpty()) {
            ObjectNode track2 = mapper.createObjectNode();
            track2.put("id", "v2");
            track2.put("type", "VIDEO");
            ArrayNode clips2 = mapper.createArrayNode();
            for (ObjectNode c : extraClips) {
                clips2.add(c);
            }
            track2.set("clips", clips2);
            tracks.add(track2);
        }
        composition.set("tracks", tracks);
        root.set("composition", composition);
        return root.toString();
    }

    private ObjectNode clip(String id, String assetId, long startFrame, long durFrame) {
        ObjectNode clip = mapper.createObjectNode();
        clip.put("id", id);
        clip.put("assetId", assetId);
        clip.set("timelineRange", range(startFrame, durFrame));
        clip.set("sourceRange", range(0, durFrame));
        return clip;
    }

    private ObjectNode range(long startFrame, long durationFrame) {
        ObjectNode rate = mapper.createObjectNode();
        rate.put("num", FPS);
        rate.put("den", 1);
        ObjectNode start = mapper.createObjectNode();
        start.put("frame", startFrame);
        start.set("rate", rate);
        ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", durationFrame);
        duration.set("rate", rate);
        ObjectNode range = mapper.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private TimelineRevisionRepository.RevisionRow row(String id, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                id, PROJECT, TENANT, null, 1, snapshotId, 0, "hash",
                "internal-1.0", "merge", "user-1", null, null, null, null, null,
                false, null, null, OffsetDateTime.now());
    }

    private void stubRevisions(String base, String source, String target) {
        when(revisionRepository.findById("rev-base")).thenReturn(Optional.of(row("rev-base", "snap-base")));
        when(revisionRepository.findById("rev-source")).thenReturn(Optional.of(row("rev-source", "snap-source")));
        when(revisionRepository.findById("rev-target")).thenReturn(Optional.of(row("rev-target", "snap-target")));
        when(snapshotService.findById("snap-base"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-base", PROJECT, TENANT, base, "internal-1.0")));
        when(snapshotService.findById("snap-source"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-source", PROJECT, TENANT, source, "internal-1.0")));
        when(snapshotService.findById("snap-target"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-target", PROJECT, TENANT, target, "internal-1.0")));
        when(snapshotService.save(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "snap-merged-" + inv.getArgument(2).hashCode());
        when(revisionRepository.nextRevisionNumber(PROJECT)).thenReturn(7);
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn("rev-target");
    }

    private TimelineMergeRequest request(String message) {
        return new TimelineMergeRequest(PROJECT, TENANT, "rev-base", "rev-source", "rev-target", "user-1", message);
    }

    private long firstClipStartFrame(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0)
                .path("timelineRange").path("start").path("frame").asLong();
    }

    private long firstClipDurFrame(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0)
                .path("timelineRange").path("duration").path("frame").asLong();
    }

    @Test
    void uneditedNonAlignedFramesSurviveRepeatedMerges() throws Exception {
        // The CRR1-FCV drift set @30fps: frames 1, 2, 4, 5, 7, 8, 10.
        for (long frame : new long[]{1, 2, 4, 5, 7, 8, 10}) {
            String base = payloadWithClipAtFrame(frame, 10);
            // source: adds an unrelated clip on track v2 — does NOT touch c1 on v1.
            // target: identical to base. The merge must materialize the v2 addition
            // and leave c1's non-aligned frame EXACTLY unchanged.
            String source = payloadWithClips(
                    clip("c1", "ast-1", frame, 10),
                    List.of(clip("c2", "ast-2", 0, 5)));
            String target = base;
            stubRevisions(base, source, target);

            TimelineMergeResult merged = engine.merge(request("rm-" + frame));
            assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status(),
                    "merge must materialize at frame=" + frame);
            assertEquals(frame, firstClipStartFrame(merged.mergedPayloadJson()),
                    "start frame drifted at frame=" + frame);
            assertEquals(10, firstClipDurFrame(merged.mergedPayloadJson()),
                    "duration drifted at frame=" + frame);
        }
    }

    @Test
    void mergedOutputRemainsGateValidAndReloadable() throws Exception {
        // Frame 7 @30fps (non-aligned): merged payload must pass the canonical
        // gate and convert back to a snapshot with the same semantic time.
        long frame = 7;
        String base = payloadWithClipAtFrame(frame, 10);
        String source = payloadWithClips(
                clip("c1", "ast-1", frame, 10),
                List.of(clip("c2", "ast-2", 0, 5)));
        stubRevisions(base, source, base);

        TimelineMergeResult merged = engine.merge(request("reload"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status());

        String mergedPayload = merged.mergedPayloadJson();
        // gate accepts the merged output (production validation path)
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(PROJECT, mergedPayload);
        assertNotNull(candidate);
        assertEquals("c1", candidate.tracks().get(0).clips().get(0).clipId());
        // C1-CNM1: conversion yields the same exact frame back (exact rational
        // MediaTime -> frame @ rate; no integer-ms step).
        var snap = com.example.platform.render.domain.timeline.diff.calculation.TimelineSnapshotConverter
                .toSnapshot(candidate, "rev-merged");
        var start = snap.tracks().get(0).clips().get(0).start();
        var rate = snap.tracks().get(0).clips().get(0).rate();
        assertEquals(frame, start.toFrameExact(rate), "reload roundtrip drifted at frame=" + frame);
    }
}
