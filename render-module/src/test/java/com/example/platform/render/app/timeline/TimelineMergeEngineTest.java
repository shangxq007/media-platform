package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.render.domain.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.render.domain.timeline.internal.TimelineMergeRequest;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
 * Canonical merge engine behavioral tests (C1 convergence).
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

    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineMergeEngine engine;
    private TimelineRevisionRepository.RevisionRow persistedRow;
    private ObjectMapper mapper = TimelineDocumentJsonSerializer.mapper();

    @BeforeEach
    void setUp() {
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        currentRevisionService = mock(ProductCurrentRevisionService.class);
        TimelineMergePreviewService previewService = new TimelineMergePreviewService(
                new com.example.platform.render.domain.timeline.diff.merge.TimelineMergeConflictDetector());
        TimelineNonConflictingMergePlanner planner =
                new TimelineNonConflictingMergePlanner(previewService);
        engine = new TimelineMergeEngine(revisionRepository, snapshotService, currentRevisionService,
                previewService, planner,
                new com.example.platform.render.domain.timeline.diff.application.TimelinePatchApplier(),
                mapper, false);
        persistedRow = null;
    }

    private TimelineDocument doc(TimelineTrack... tracks) {
        return new TimelineDocument("timeline-1.0", List.of(tracks), TimelineMetadata.empty());
    }

    private TimelineTrack track(String id, TimelineClip... clips) {
        return new TimelineTrack(id, id, TrackType.VIDEO, List.of(clips));
    }

    private TimelineClip clip(String id, long startMs, long durationMs) {
        return new TimelineClip(id, "asset-" + id,
                Duration.ofMillis(startMs), Duration.ofMillis(startMs + durationMs),
                Duration.ZERO, Duration.ofMillis(durationMs));
    }

    private void stubRevisions(String baseJson, String sourceJson, String targetJson) {
        when(revisionRepository.findById(BASE_REV))
                .thenReturn(Optional.of(row(BASE_REV, "snap-base")));
        when(revisionRepository.findById(SOURCE_REV))
                .thenReturn(Optional.of(row(SOURCE_REV, "snap-source")));
        when(revisionRepository.findById(TARGET_REV))
                .thenReturn(Optional.of(row(TARGET_REV, "snap-target")));
        when(snapshotService.findPayload("snap-base")).thenReturn(Optional.of(baseJson));
        when(snapshotService.findPayload("snap-source")).thenReturn(Optional.of(sourceJson));
        when(snapshotService.findPayload("snap-target")).thenReturn(Optional.of(targetJson));
        when(snapshotService.save(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "snap-merged-" + inv.getArgument(2).hashCode());
        when(revisionRepository.nextRevisionNumber(PROJECT)).thenReturn(7);
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn(TARGET_REV);
    }

    private TimelineRevisionRepository.RevisionRow row(String id, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                id, PROJECT, TENANT, null, 1, snapshotId, 0, "hash",
                "timeline-1.0", "test", "user-1", null, null, null, null, null,
                false, null, null, OffsetDateTime.now());
    }

    private TimelineMergeRequest request(String message) {
        return new TimelineMergeRequest(PROJECT, TENANT, BASE_REV, SOURCE_REV, TARGET_REV, "user-1", message);
    }

    private String json(TimelineDocument d) throws Exception {
        return mapper.writeValueAsString(d);
    }

    private void captureInsert() {
        org.mockito.Mockito.doAnswer(inv -> {
            persistedRow = inv.getArgument(0);
            return null;
        }).when(revisionRepository).insert(any(TimelineRevisionRepository.RevisionRow.class));
    }

    private TimelineDocument parse(String payload) throws Exception {
        return mapper.readValue(payload, TimelineDocument.class);
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
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 3000, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 0, 5000)));
        stubRevisions(json(base), json(source), json(target));
        captureInsert();

        TimelineMergeResult result = engine.merge(request("source-only"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
        assertEquals(SOURCE_REV + "," + TARGET_REV, persistedRow.mergeParentRevisionIds());
        assertEquals(3000L, parse(result.mergedPayloadJson()).getTracks().get(0).clips().get(0).getStartTime().toMillis());
    }

    // 2. target-only change materialized
    @Test
    void targetOnlyChangeIsMaterialized() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 0, 4000)));
        stubRevisions(json(base), json(source), json(target));
        captureInsert();

        TimelineMergeResult result = engine.merge(request("target-only"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        assertEquals(4000L, parse(result.mergedPayloadJson()).getTracks().get(0).clips().get(0).getEndTime().toMillis()
                - parse(result.mergedPayloadJson()).getTracks().get(0).clips().get(0).getStartTime().toMillis());
    }

    // 3. C1-RED-05: disjoint typed paths (track reorder vs clip move) — BOTH materialized
    @Test
    void sameEntityDisjointPathsBothMaterialized() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)), track("t2"));
        // source: reorder tracks (t2 first) — touches track[t2].order / track[t1].order only
        TimelineDocument source = doc(track("t2"), track("t1", clip("c1", 0, 5000)));
        // target: move clip c1 — touches clip[c1].startMs + timeline.durationMs only
        TimelineDocument target = doc(track("t1", clip("c1", 3000, 5000)), track("t2"));
        stubRevisions(json(base), json(source), json(target));
        captureInsert();

        TimelineMergeResult result = engine.merge(request("disjoint"));

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status());
        List<TimelineTrack> mergedTracks = parse(result.mergedPayloadJson()).getTracks();
        assertEquals("t2", mergedTracks.get(0).trackId(), "source track reorder must be materialized");
        TimelineClip merged = mergedTracks.stream()
                .filter(t -> t.trackId().equals("t1")).findFirst()
                .orElseThrow().clips().get(0);
        assertEquals(3000L, merged.getStartTime().toMillis(), "target clip move must be materialized");
    }

    // 4. same-path divergent change -> CONFLICTS
    @Test
    void samePathConflict() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 3000, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 4000, 5000)));
        stubRevisions(json(base), json(source), json(target));

        TimelineMergeResult result = engine.merge(request("same-path"));

        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status());
        assertTrue(result.hasConflicts());
    }

    // 5. delete vs modify -> explicit CONFLICT (C1-RED-06)
    @Test
    void deleteVsModifyConflict() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc();                                          // clip removed
        TimelineDocument target = doc(track("t1", clip("c1", 2000, 5000)));       // clip moved
        stubRevisions(json(base), json(source), json(target));

        TimelineMergeResult result = engine.merge(request("delete-vs-modify"));

        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status());
        assertTrue(result.hasConflicts());
    }

    // 6. stale-current rejected
    @Test
    void staleCurrentRejected() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 3000, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 0, 5000)));
        stubRevisions(json(base), json(source), json(target));
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn("rev-newer");

        assertThrows(TimelineConflictException.class, () -> engine.merge(request("stale")));
    }

    // 7. idempotent retry returns existing merge revision
    @Test
    void idempotentRetryReturnsDuplicate() throws Exception {
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 3000, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 0, 5000)));
        stubRevisions(json(base), json(source), json(target));
        String payload = json(doc(track("t1", clip("c1", 3000, 5000))));
        String hash = computeHash(payload);
        TimelineRevisionRepository.RevisionRow existing = new TimelineRevisionRepository.RevisionRow(
                "rev-dup", PROJECT, TENANT, TARGET_REV, 6, "snap-dup", 0, hash,
                "timeline-1.0", "merge", "user-1", null, "dup", null, null, null,
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
        TimelineDocument base = doc(track("t1", clip("c1", 0, 5000)));
        TimelineDocument source = doc(track("t1", clip("c1", 3000, 5000)));
        TimelineDocument target = doc(track("t1", clip("c1", 0, 4000)));
        stubRevisions(json(base), json(source), json(target));
        captureInsert();
        TimelineMergeResult first = engine.merge(request("det-1"));
        captureInsert();
        TimelineMergeResult second = engine.merge(request("det-2"));
        assertEquals(first.mergedPayloadJson(), second.mergedPayloadJson());
    }
}
