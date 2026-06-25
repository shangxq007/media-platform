package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineMergeServiceTest {

    private TimelineRevisionService revisionService;
    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private TimelineSemanticDiffService semanticDiffService;
    private TimelineConflictDetector conflictDetector;
    private TimelineConflictResolver conflictResolver;
    private TimelineMergeService mergeService;

    @BeforeEach
    void setUp() {
        revisionService = mock(TimelineRevisionService.class);
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        semanticDiffService = mock(TimelineSemanticDiffService.class);
        conflictDetector = new TimelineConflictDetector();
        conflictResolver = new TimelineConflictResolver();
        mergeService = new TimelineMergeService(
                revisionService, revisionRepository, snapshotService,
                semanticDiffService, conflictDetector, conflictResolver);
    }

    private static TimelineRevisionRepository.RevisionRow rev(String id, int num, String snapId) {
        return new TimelineRevisionRepository.RevisionRow(
                id, "proj_1", "tenant_1", null, num, snapId, 0, "hash_" + id,
                "internal-1.0", "sync", null, null, null, null, null, null,
                false, null, null, java.time.OffsetDateTime.now());
    }

    @Test
    void noOpMergeWhenStructurallyEqual() throws Exception {
        var base = rev("trev_base", 1, "snap_base");
        var src = rev("trev_src", 2, "snap_src");
        var tgt = rev("trev_tgt", 3, "snap_tgt");

        when(revisionRepository.findById("trev_base")).thenReturn(Optional.of(base));
        when(revisionRepository.findById("trev_src")).thenReturn(Optional.of(src));
        when(revisionRepository.findById("trev_tgt")).thenReturn(Optional.of(tgt));

        when(snapshotService.findPayload("snap_base")).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_src")).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_tgt")).thenReturn(Optional.of("{}"));

        var noOpResult = new SemanticDiffResult("tl", "tl", 1, 1, "1.0", List.of(), true);
        when(semanticDiffService.diff(any(), any())).thenReturn(noOpResult);

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertNotNull(result);
        assertEquals(MergeStatus.NO_OP, result.status());
        assertNull(result.mergedRevisionId());
    }

    @Test
    void mergeWithConflictsReturnsConflictsStatus() throws Exception {
        var base = rev("trev_base", 1, "snap_base");
        var src = rev("trev_src", 2, "snap_src");
        var tgt = rev("trev_tgt", 3, "snap_tgt");

        when(revisionRepository.findById("trev_base")).thenReturn(Optional.of(base));
        when(revisionRepository.findById("trev_src")).thenReturn(Optional.of(src));
        when(revisionRepository.findById("trev_tgt")).thenReturn(Optional.of(tgt));

        when(snapshotService.findPayload("snap_base")).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_src")).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_tgt")).thenReturn(Optional.of("{}"));

        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_1");
        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim src");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim tgt");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(eq("{}"), eq("{}")))
                .thenReturn(srcDiff)
                .thenReturn(tgtDiff);

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertNotNull(result);
        assertEquals(MergeStatus.CONFLICTS, result.status());
        assertEquals(1, result.conflicts().size());
        assertNull(result.mergedRevisionId());
    }

    @Test
    void mergeRejectsInvalidBaseRevision() {
        when(revisionRepository.findById("trev_nonexistent")).thenReturn(Optional.empty());

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_nonexistent", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertEquals(MergeStatus.FAILED, result.status());
        assertTrue(result.summary().contains("failed"));
    }
}
