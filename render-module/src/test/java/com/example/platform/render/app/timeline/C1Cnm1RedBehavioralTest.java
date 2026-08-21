package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.InternalTimelineJson;import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C1-CNM1-RED-04/05/06/13 behavioral proofs.
 *
 * <p>RED-04: fractional-rate save -> merge -> persist -> reload exact
 * (24000/1001, 30000/1001, 60000/1001). RED-05: repeated merge drift = 0.
 * RED-06: existing clip effects survive merge reconstruction.
 * RED-13: clip identity != asset identity through write->parse->merge.</p>
 */
class C1Cnm1RedBehavioralTest {

    private static final String PROJECT = "proj-cnm1-red";
    private static final String TENANT = "tenant-cnm1-red";

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
                new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector());
        TimelineNonConflictingMergePlanner planner = new TimelineNonConflictingMergePlanner(previewService);
        org.jooq.DSLContext dslMockC1Cn0 = org.mockito.Mockito.mock(org.jooq.DSLContext.class);
org.jooq.Configuration cfgdslMockC1Cn0 = org.mockito.Mockito.mock(org.jooq.Configuration.class);
        org.jooq.DSLContext txDsldslMockC1Cn0 = org.mockito.Mockito.mock(org.jooq.DSLContext.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(cfgdslMockC1Cn0.dsl()).thenReturn(txDsldslMockC1Cn0);
        org.mockito.Mockito.when(dslMockC1Cn0.transactionResult(org.mockito.ArgumentMatchers.<org.jooq.TransactionalCallable<Object>>any()))
                .thenAnswer(inv -> {
                    org.jooq.TransactionalCallable<Object> callable = inv.getArgument(0);
                    return callable.run(cfgdslMockC1Cn0);
                });
engine = new TimelineMergeEngine(revisionRepository, snapshotService, currentRevisionService,
                previewService, planner,
                new com.example.platform.timeline.diff.application.TimelinePatchApplier(),
                mapper,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                dslMockC1Cn0);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── payload builders (internal-1.0 wire shape) ───

    private ObjectNode range(long startFrame, long durFrames, long num, long den) {
        ObjectNode rate = mapper.createObjectNode();
        rate.put("num", num);
        rate.put("den", den);
        ObjectNode start = mapper.createObjectNode();
        start.put("frame", startFrame);
        start.set("rate", rate);
        ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", durFrames);
        duration.set("rate", rate);
        ObjectNode range = mapper.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private ObjectNode clip(String id, String assetId, long startFrame, long durFrames,
                            long num, long den, boolean withEffect) {
        ObjectNode c = mapper.createObjectNode();
        c.put("id", id);
        c.put("assetId", assetId);
        c.set("timelineRange", range(startFrame, durFrames, num, den));
        ObjectNode srcStart = mapper.createObjectNode();
        srcStart.put("frame", 0);
        srcStart.set("rate", range(0, 1, num, den).path("start").path("rate"));
        ObjectNode srcDur = mapper.createObjectNode();
        srcDur.put("frame", durFrames);
        srcDur.set("rate", range(0, 1, num, den).path("start").path("rate"));
        ObjectNode srcRange = mapper.createObjectNode();
        srcRange.set("start", srcStart);
        srcRange.set("duration", srcDur);
        c.set("sourceRange", srcRange);
        if (withEffect) {
            ArrayNode effects = mapper.createArrayNode();
            ObjectNode fx = mapper.createObjectNode();
            fx.put("id", "fx-1");
            fx.put("effectKey", "blur");
            ObjectNode params = mapper.createObjectNode();
            params.put("radius", 5);
            fx.set("parameters", params);
            effects.add(fx);
            c.set("effects", effects);
        }
        return c;
    }

    private String payload(ObjectNode primaryClip, boolean extraTrack) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-cnm1-red");
        ObjectNode composition = mapper.createObjectNode();
        ArrayNode tracks = mapper.createArrayNode();
        ObjectNode track1 = mapper.createObjectNode();
        track1.put("id", "v1");
        ArrayNode clips1 = mapper.createArrayNode();
        clips1.add(primaryClip);
        track1.set("clips", clips1);
        tracks.add(track1);
        if (extraTrack) {
            ObjectNode track2 = mapper.createObjectNode();
            track2.put("id", "v2");
            ArrayNode clips2 = mapper.createArrayNode();
            clips2.add(clip("c2", "ast-2", 0, 5, 30, 1, false));
            track2.set("clips", clips2);
            tracks.add(track2);
        }
        composition.set("tracks", tracks);
        root.set("composition", composition);
        return root.toString();
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
        when(snapshotService.findOwnedById(PROJECT, TENANT, "snap-base"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-base", PROJECT, TENANT, base, "internal-1.0")));
        when(snapshotService.findById("snap-source"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-source", PROJECT, TENANT, source, "internal-1.0")));
        when(snapshotService.findOwnedById(PROJECT, TENANT, "snap-source"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-source", PROJECT, TENANT, source, "internal-1.0")));
        when(snapshotService.findById("snap-target"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-target", PROJECT, TENANT, target, "internal-1.0")));
        when(snapshotService.findOwnedById(PROJECT, TENANT, "snap-target"))
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

    private long firstClipRateNum(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0)
                .path("timelineRange").path("start").path("rate").path("num").asLong();
    }

    private long firstClipRateDen(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0)
                .path("timelineRange").path("start").path("rate").path("den").asLong();
    }

    private long firstClipEffectCount(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        JsonNode effects = root.path("composition").path("tracks").path(0).path("clips").path(0).path("effects");
        return effects.isArray() ? effects.size() : 0;
    }

    private String firstClipAssetId(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0).path("assetId").asText("");
    }

    // ─── RED-04: fractional-rate save -> merge -> reload exact ───

    @Test
    void fractionalRateDenominatorSurvivesMerge() throws Exception {
        for (long[] rate : new long[][]{{24000, 1001}, {30000, 1001}, {60000, 1001}}) {
            long num = rate[0], den = rate[1];
            String base = payload(clip("c1", "ast-1", 30, 10, num, den, false), false);
            String source = payload(clip("c1", "ast-1", 30, 10, num, den, false), true);
            String target = base;
            stubRevisions(base, source, target);
            TimelineMergeResult merged = engine.merge(request("fr-" + num));
            assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status());
            String out = merged.mergedPayloadJson();
            assertEquals(30L, firstClipStartFrame(out), "frame preserved @ " + num + "/" + den);
            assertEquals(num, firstClipRateNum(out), "rate num preserved @ " + num + "/" + den);
            assertEquals(den, firstClipRateDen(out), "rate den preserved @ " + num + "/" + den);
        }
    }

    // ─── RED-05: repeated merge drift = 0 at fractional rate ───

    @Test
    void repeatedMergeDriftIsZeroAtFractionalRate() throws Exception {
        long num = 30000, den = 1001;
        String p = payload(clip("c1", "ast-1", 30, 10, num, den, false), false);
        for (int round = 0; round < 3; round++) {
            // source adds a NEW unrelated track+clip each round; c1 on v1 is
            // never touched — unedited time/rate fields must be EXACTLY
            // unchanged across repeated merges (idempotent, drift-free).
            String source = payloadWithExtraTrack(p, round);
            stubRevisions(p, source, p);
            TimelineMergeResult merged = engine.merge(request("rep-" + round));
            if (merged.status() != TimelineMergeResult.MergeStatus.MERGED) {
                System.out.println("CNM1-REP-FAIL round=" + round + " status=" + merged.status()
                        + " summary=" + merged.summary());
            }
            assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status(),
                    "round " + round + " must materialize a new source change");
            String out = merged.mergedPayloadJson();
            if (round == 0) {
                System.out.println("CNM1-REP-ROUND0-OUT=" + out);
            }
            assertEquals(30L, firstClipStartFrame(out), "drift at round " + round);
            assertEquals(den, firstClipRateDen(out), "den loss at round " + round);
            p = out;
        }
    }

    /** Returns a payload equal to p plus one new track v2/v3/v4 carrying one clip. */
    private String payloadWithExtraTrack(String p, int round) throws Exception {
        JsonNode root = InternalTimelineJson.parse(p);
        ObjectNode copy = ((ObjectNode) root).deepCopy();
        ObjectNode composition = (ObjectNode) copy.path("composition");
        ArrayNode tracks = (ArrayNode) composition.path("tracks");
        String trackId = "v" + (2 + round);
        ObjectNode track = mapper.createObjectNode();
        track.put("id", trackId);
        track.put("type", "VIDEO");
        ArrayNode clips = mapper.createArrayNode();
        clips.add(clip("c" + (10 + round), "ast-" + (10 + round), 0, 5, 30, 1, false));
        track.set("clips", clips);
        tracks.add(track);
        return copy.toString();
    }

    // ─── RED-06: effects survive merge reconstruction ───

    @Test
    void clipEffectsSurviveMergeReconstruction() throws Exception {
        String base = payload(clip("c1", "ast-1", 0, 10, 30, 1, true), false);
        // source adds unrelated track — does not touch c1's effects
        String source = payload(clip("c1", "ast-1", 0, 10, 30, 1, true), true);
        String target = base;
        stubRevisions(base, source, target);
        TimelineMergeResult merged = engine.merge(request("fx-preserve"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status());
        String out = merged.mergedPayloadJson();
        assertEquals(1L, firstClipEffectCount(out), "effect must survive merge reconstruction");
    }

    @Test
    void effectAbsenceRemainsAbsence() throws Exception {
        String base = payload(clip("c1", "ast-1", 0, 10, 30, 1, false), false);
        String source = payload(clip("c1", "ast-1", 0, 10, 30, 1, false), true);
        stubRevisions(base, source, base);
        TimelineMergeResult merged = engine.merge(request("fx-absent"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status());
        assertEquals(0L, firstClipEffectCount(merged.mergedPayloadJson()), "absence must remain absence");
    }

    // ─── RED-13: clip identity != asset identity through merge ───

    @Test
    void clipIdentityAndAssetIdentityRemainDistinct() throws Exception {
        String base = payload(clip("clip_001", "ast_smoke_001", 0, 10, 30, 1, true), false);
        String source = payload(clip("clip_001", "ast_smoke_001", 0, 10, 30, 1, true), true);
        stubRevisions(base, source, base);
        TimelineMergeResult merged = engine.merge(request("identity"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, merged.status());
        String out = merged.mergedPayloadJson();
        JsonNode root = InternalTimelineJson.parse(out);
        JsonNode clipNode = root.path("composition").path("tracks").path(0).path("clips").path(0);
        assertEquals("clip_001", clipNode.path("id").asText(), "clip identity must be preserved");
        assertEquals("ast_smoke_001", firstClipAssetId(out), "asset identity must be preserved, distinct from clip id");
        // the two identities are distinct in the merged payload
        assertNotEquals(clipNode.path("id").asText(), firstClipAssetId(out));
    }
}
