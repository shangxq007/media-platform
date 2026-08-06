package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineSnapshotService.SnapshotInfo;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineDiagnosticCode;
import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import com.example.platform.shared.web.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * E1C merge unit tests (updated for the gated flow; frozen assertions preserved where
 * behavior is unchanged). Uses the Spring-authoritative seven-argument constructor so
 * the canonical gates, idempotency scan and current-revision update are exercised with
 * mocks; real-PostgreSQL persistence claims are proven in
 * TimelineMergeServiceE1cGateIntegrationTest.
 */
class TimelineMergeServiceTest {

    private TimelineRevisionService revisionService;
    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private TimelineSemanticDiffService semanticDiffService;
    private TimelineConflictDetector conflictDetector;
    private TimelineConflictResolver conflictResolver;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineMergeService mergeService;

    /** Canonically VALID internal-1.0 payload (single clip; passes the E1c canonical gate). */
    private static final String VALID_PAYLOAD = """
            {"schemaVersion":"1.0","id":"tl-unit",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    /** Canonically INVALID internal-1.0 payload (duplicate clip id c1). */
    private static final String INVALID_PAYLOAD = """
            {"schemaVersion":"1.0","id":"tl-unit",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c1","assetId":"ast-2",
                  "timelineRange":{"start":{"frame":30,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant_1");
        revisionService = mock(TimelineRevisionService.class);
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        semanticDiffService = mock(TimelineSemanticDiffService.class);
        conflictDetector = new TimelineConflictDetector();
        conflictResolver = new TimelineConflictResolver();
        currentRevisionService = mock(ProductCurrentRevisionService.class);
        mergeService = new TimelineMergeService(
                revisionService, revisionRepository, snapshotService,
                semanticDiffService, conflictDetector, conflictResolver,
                currentRevisionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static TimelineRevisionRepository.RevisionRow rev(String id, int num, String snapId) {
        return new TimelineRevisionRepository.RevisionRow(
                id, "proj_1", "tenant_1", null, num, snapId, 0, "hash_" + id,
                "internal-1.0", "sync", null, null, null, null, null, null,
                false, null, null, java.time.OffsetDateTime.now());
    }

    private void stubRevisions(String baseId, String srcId, String tgtId) {
        when(revisionRepository.findById(baseId)).thenReturn(Optional.of(rev(baseId, 1, "snap_base")));
        when(revisionRepository.findById(srcId)).thenReturn(Optional.of(rev(srcId, 2, "snap_src")));
        when(revisionRepository.findById(tgtId)).thenReturn(Optional.of(rev(tgtId, 3, "snap_tgt")));
        when(snapshotService.findById("snap_base"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_base", "proj_1", "tenant_1", VALID_PAYLOAD, "internal-1.0")));
        when(snapshotService.findById("snap_src"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_src", "proj_1", "tenant_1", VALID_PAYLOAD, "internal-1.0")));
        when(snapshotService.findById("snap_tgt"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_tgt", "proj_1", "tenant_1", VALID_PAYLOAD, "internal-1.0")));
        when(revisionRepository.listByProject("proj_1", 200)).thenReturn(List.of());
        when(snapshotService.save(any(), any(), any(), any())).thenReturn("snap_merged");
        when(revisionRepository.nextRevisionNumber("proj_1")).thenReturn(99);
    }

    private static TimelineMergeRequest request() {
        return new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);
    }

    @Test
    void noOpMergeWhenStructurallyEqual() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        var noOpResult = new SemanticDiffResult("tl", "tl", 1, 1, "1.0", List.of(), true);
        when(semanticDiffService.diff(any(), any())).thenReturn(noOpResult);

        TimelineMergeResult result = mergeService.threeWayMerge(request());

        assertNotNull(result);
        assertEquals(MergeStatus.NO_OP, result.status());
        assertNull(result.mergedRevisionId());
        verify(snapshotService, never()).save(any(), any(), any(), any());
    }

    @Test
    void mergeWithConflictsReturnsConflictsStatus() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_1");
        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim src");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim tgt");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(any(), any())).thenReturn(srcDiff).thenReturn(tgtDiff);

        TimelineMergeResult result = mergeService.threeWayMerge(request());

        assertNotNull(result);
        assertEquals(MergeStatus.CONFLICTS, result.status());
        assertEquals(1, result.conflicts().size());
        assertNull(result.mergedRevisionId());
        verify(snapshotService, never()).save(any(), any(), any(), any());
        verify(revisionRepository, never()).insert(any());
    }

    @Test
    void mergeRejectsInvalidBaseRevision() {
        when(revisionRepository.findById("trev_nonexistent")).thenReturn(Optional.empty());

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_nonexistent", "trev_src", "trev_tgt", null, null);

        // E1c: a missing revision is a classified failure RETHROWN (the frozen
        // transaction contract replaces the base's swallow-into-FAILED behavior).
        assertThrows(IllegalArgumentException.class, () -> mergeService.threeWayMerge(req));
    }

    @Test
    void mergedMergePersistsAndAdvancesCurrentRevision() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED,
                new EntityRef(EntityKind.CLIP, "clip_a"), "trim A");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_ADDED,
                new EntityRef(EntityKind.CLIP, "clip_b"), "add B");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(any(), any())).thenReturn(srcDiff).thenReturn(tgtDiff);

        TimelineMergeResult result = mergeService.threeWayMerge(request());

        assertEquals(MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
        // write ordering: snapshot, then revision, then current-revision update
        verify(snapshotService).save(eq("proj_1"), eq("tenant_1"), any(), eq("internal-1.0"));
        verify(revisionRepository).insert(any());
        verify(currentRevisionService).updateCurrentRevision(
                eq("proj_1"), eq("trev_tgt"), eq(result.mergedRevisionId()));
    }

    @Test
    void canonicalInvalidInputRejectedWithOrderedDiagnostics() {
        stubRevisions("trev_base", "trev_src", "trev_tgt");
        // overwrite the source payload with a canonical-invalid one (duplicate clip id)
        when(snapshotService.findById("snap_src"))
                .thenReturn(Optional.of(new SnapshotInfo("snap_src", "proj_1", "tenant_1", INVALID_PAYLOAD, "internal-1.0")));

        TimelineCanonicalRejectionException ex = assertThrows(
                TimelineCanonicalRejectionException.class, () -> mergeService.threeWayMerge(request()));

        assertFalse(ex.diagnostics().isEmpty(), "rejection must carry canonical diagnostics");
        assertTrue(ex.diagnostics().stream()
                        .anyMatch(d -> d.code() == TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE),
                "expected TIMELINE_CLIP_ID_DUPLICATE diagnostic");
        verify(snapshotService, never()).save(any(), any(), any(), any());
        verify(revisionRepository, never()).insert(any());
    }
}
