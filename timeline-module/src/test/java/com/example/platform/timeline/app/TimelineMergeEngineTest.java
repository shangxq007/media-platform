package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.InternalTimelineJson;import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineMergeEngine;import com.example.platform.timeline.app.TimelineRevisionService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import com.example.platform.timeline.version.TimelineConflictException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Canonical merge engine behavioral tests (C1 convergence, C1-CRR1 corrected).
 *
 * <p>C1-CRR1: fixtures are the canonical PERSISTED revision payload format
 * (internal-1.0: schemaVersion "1.0", composition block) — the exact format
 * produced by {@code TimelineRevisionService.recordRevision} (production save
 * path). Canonical gates are always enabled (no bypass flag). The engine's
 * input conversion consumes internal-1.0 via the E1b gate adapter and its
 * output is rebuilt as internal-1.0, so every test exercises the corrected
 * contract.</p>
 *
 * <p>Covers: source-only / target-only, same-entity disjoint typed paths
 * (C1-RED-05 materialization), same-path conflict, delete-vs-modify conflict
 * (C1-RED-06), dual-parent revision, stale-current rejection, idempotent retry,
 * deterministic result.</p>
 */
class TimelineMergeEngineTest {

    private static final String PROJECT = "proj-1";
    private static final String TENANT = "tenant-1";
    private static final String BASE_REV = "rev-base";
    private static final String SOURCE_REV = "rev-source";
    private static final String TARGET_REV = "rev-target";
    private static final int FPS = 30;

    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineMergeEngine engine;
    private TimelineRevisionRepository.RevisionRow persistedRow;
    private ObjectMapper mapper = InternalTimelineJson.mapper();

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        currentRevisionService = mock(ProductCurrentRevisionService.class);
        TimelineMergePreviewService previewService = new TimelineMergePreviewService(
                new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector());
        TimelineNonConflictingMergePlanner planner =
                new TimelineNonConflictingMergePlanner(previewService);
        engine = new TimelineMergeEngine(revisionRepository, snapshotService, currentRevisionService,
                previewService, planner,
                new com.example.platform.timeline.diff.application.TimelinePatchApplier(),
                mapper,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class));
        persistedRow = null;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── internal-1.0 fixtures (production save format: schemaVersion "1.0", composition) ──

    private ObjectNode internalTimeline(String id, JsonNode... tracks) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", id);
        ObjectNode composition = mapper.createObjectNode();
        ArrayNode trackArray = mapper.createArrayNode();
        for (JsonNode track : tracks) {
            trackArray.add(track);
        }
        composition.set("tracks", trackArray);
        root.set("composition", composition);
        return root;
    }

    private ObjectNode trackNode(String id, JsonNode... clips) {
        ObjectNode track = mapper.createObjectNode();
        track.put("id", id);
        track.put("type", "VIDEO");
        ArrayNode clipArray = mapper.createArrayNode();
        for (JsonNode clip : clips) {
            clipArray.add(clip);
        }
        track.set("clips", clipArray);
        return track;
    }

    private ObjectNode clipNode(String id, long startMs, long durationMs) {
        ObjectNode clip = mapper.createObjectNode();
        clip.put("id", id);
        clip.put("assetId", "asset-" + id);
        clip.set("timelineRange", rangeNode(startMs, durationMs));
        clip.set("sourceRange", rangeNode(0L, durationMs));
        return clip;
    }

    private ObjectNode rangeNode(long startMs, long durationMs) {
        ObjectNode rate = mapper.createObjectNode();
        rate.put("num", FPS);
        rate.put("den", 1);
        ObjectNode start = mapper.createObjectNode();
        start.put("frame", (startMs * FPS) / 1000L);
        start.set("rate", rate);
        ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", (durationMs * FPS) / 1000L);
        duration.set("rate", rate);
        ObjectNode range = mapper.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private void stubRevisions(String baseJson, String sourceJson, String targetJson) {
        when(revisionRepository.findById(BASE_REV))
                .thenReturn(Optional.of(row(BASE_REV, "snap-base")));
        when(revisionRepository.findById(SOURCE_REV))
                .thenReturn(Optional.of(row(SOURCE_REV, "snap-source")));
        when(revisionRepository.findById(TARGET_REV))
                .thenReturn(Optional.of(row(TARGET_REV, "snap-target")));
        // Canonical gates always on: loadPayload resolves snapshots via findById (tenant-aware).
        when(snapshotService.findById("snap-base"))
                .thenReturn(Optional.of(info("snap-base", baseJson)));
        when(snapshotService.findById("snap-source"))
                .thenReturn(Optional.of(info("snap-source", sourceJson)));
        when(snapshotService.findById("snap-target"))
                .thenReturn(Optional.of(info("snap-target", targetJson)));
        when(snapshotService.save(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "snap-merged-" + inv.getArgument(2).hashCode());
        when(revisionRepository.nextRevisionNumber(PROJECT)).thenReturn(7);
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn(TARGET_REV);
    }

    private TimelineSnapshotService.SnapshotInfo info(String id, String payload) {
        return new TimelineSnapshotService.SnapshotInfo(id, PROJECT, TENANT, payload, "internal-1.0");
    }

    private TimelineRevisionRepository.RevisionRow row(String id, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                id, PROJECT, TENANT, null, 1, snapshotId, 0, "hash",
                "internal-1.0", "test", "user-1", null, null, null, null, null,
                false, null, null, OffsetDateTime.now());
    }

    private TimelineMergeRequest request(String message) {
        return new TimelineMergeRequest(PROJECT, TENANT, BASE_REV, SOURCE_REV, TARGET_REV, "user-1", message);
    }

    private String json(JsonNode node) throws Exception {
        return InternalTimelineJson.write(node);
    }

    private void captureInsert() {
        org.mockito.Mockito.doAnswer(inv -> {
            persistedRow = inv.getArgument(0);
            return null;
        }).when(revisionRepository).insert(any(TimelineRevisionRepository.RevisionRow.class));
    }

    /** Read the merged payload (internal-1.0) and return the first track's first clip. */
    private JsonNode firstClip(String payload) throws Exception {
        JsonNode root = InternalTimelineJson.parse(payload);
        return root.path("composition").path("tracks").path(0).path("clips").path(0);
    }

    private long clipStartMs(JsonNode clip) {
        return clip.path("timelineRange").path("start").path("frame").asLong() * 1000L / FPS;
    }

    private long clipDurationMs(JsonNode clip) {
        return clip.path("timelineRange").path("duration").path("frame").asLong() * 1000L / FPS;
    }

    private static String computeHash(String payload) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String input = "merge:" + SOURCE_REV + ":" + TARGET_REV + ":" + payload;
        byte[] h = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(64);
        for (byte b : h) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // 1. source-only change materialized
    @Test
    void sourceOnlyChangeIsMaterialized() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        stubRevisions(base, source, target);
        captureInsert();

        TimelineMergeResult result = engine.merge(request("source-only"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
        assertEquals(SOURCE_REV + "," + TARGET_REV, persistedRow.mergeParentRevisionIds());
        assertEquals(3000L, clipStartMs(firstClip(result.mergedPayloadJson())));
    }

    // 2. target-only change materialized
    @Test
    void targetOnlyChangeIsMaterialized() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 4000))));
        stubRevisions(base, source, target);
        captureInsert();

        TimelineMergeResult result = engine.merge(request("target-only"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertEquals(4000L, clipDurationMs(firstClip(result.mergedPayloadJson())));
    }

    // 3. C1-RED-05: disjoint typed paths (track reorder vs clip move) — BOTH materialized
    @Test
    void sameEntityDisjointPathsBothMaterialized() throws Exception {
        String base = json(internalTimeline("tl-1",
                trackNode("t1", clipNode("c1", 0, 5000)), trackNode("t2")));
        // source: reorder tracks (t2 first) — touches track[t2].order / track[t1].order only
        String source = json(internalTimeline("tl-1",
                trackNode("t2"), trackNode("t1", clipNode("c1", 0, 5000))));
        // target: move clip c1 — touches clip[c1].startMs + timeline.durationMs only
        String target = json(internalTimeline("tl-1",
                trackNode("t1", clipNode("c1", 3000, 5000)), trackNode("t2")));
        stubRevisions(base, source, target);
        captureInsert();

        TimelineMergeResult result = engine.merge(request("disjoint"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        JsonNode root = InternalTimelineJson.parse(result.mergedPayloadJson());
        JsonNode mergedTracks = root.path("composition").path("tracks");
        assertEquals("t2", mergedTracks.path(0).path("id").asText(), "source track reorder must be materialized");
        JsonNode mergedClip = mergedTracks.path(1).path("clips").path(0);
        assertEquals(3000L, clipStartMs(mergedClip), "target clip move must be materialized");
    }

    // 4. same-path divergent change -> CONFLICTS
    @Test
    void samePathConflict() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 4000, 5000))));
        stubRevisions(base, source, target);

        TimelineMergeResult result = engine.merge(request("same-path"));

        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status());
        assertTrue(result.hasConflicts());
    }

    // 5. delete vs modify -> explicit CONFLICT (C1-RED-06)
    @Test
    void deleteVsModifyConflict() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1"));                                  // clip removed
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 2000, 5000)))); // clip moved
        stubRevisions(base, source, target);

        TimelineMergeResult result = engine.merge(request("delete-vs-modify"));

        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status());
        assertTrue(result.hasConflicts());
    }

    // 6. stale-current rejected
    @Test
    void staleCurrentRejected() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        stubRevisions(base, source, target);
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn("rev-newer");

        assertThrows(TimelineConflictException.class, () -> engine.merge(request("stale")));
    }

    // 7. idempotent retry returns existing merge revision
    @Test
    void idempotentRetryReturnsDuplicate() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        stubRevisions(base, source, target);
        String payload = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String hash = computeHash(payload);
        TimelineRevisionRepository.RevisionRow existing = new TimelineRevisionRepository.RevisionRow(
                "rev-dup", PROJECT, TENANT, TARGET_REV, 6, "snap-dup", 0, hash,
                "internal-1.0", "merge", "user-1", null, "dup", null, null, null,
                true, SOURCE_REV + "," + TARGET_REV, BASE_REV, OffsetDateTime.now());
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of(existing));
        when(snapshotService.findById("snap-dup")).thenReturn(Optional.of(
                new TimelineSnapshotService.SnapshotInfo("snap-dup", PROJECT, TENANT, payload, "internal-1.0")));

        TimelineMergeResult result = engine.merge(request("retry"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertEquals("rev-dup", result.mergedRevisionId());
    }

    // 8. deterministic: identical inputs produce identical merged payload
    @Test
    void deterministicMergeResult() throws Exception {
        String base = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 5000))));
        String source = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 3000, 5000))));
        String target = json(internalTimeline("tl-1", trackNode("t1", clipNode("c1", 0, 4000))));
        stubRevisions(base, source, target);
        captureInsert();
        TimelineMergeResult first = engine.merge(request("det-1"));
        captureInsert();
        TimelineMergeResult second = engine.merge(request("det-2"));
        assertEquals(first.mergedPayloadJson(), second.mergedPayloadJson());
    }
}
