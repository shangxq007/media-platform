package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineMergeApplicationTest {

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

    private void stubRevisions(String baseId, String srcId, String tgtId) {
        var base = rev(baseId, 1, "snap_" + baseId);
        var src = rev(srcId, 2, "snap_" + srcId);
        var tgt = rev(tgtId, 3, "snap_" + tgtId);
        when(revisionRepository.findById(baseId)).thenReturn(Optional.of(base));
        when(revisionRepository.findById(srcId)).thenReturn(Optional.of(src));
        when(revisionRepository.findById(tgtId)).thenReturn(Optional.of(tgt));
        when(snapshotService.findPayload("snap_" + baseId)).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_" + srcId)).thenReturn(Optional.of("{}"));
        when(snapshotService.findPayload("snap_" + tgtId)).thenReturn(Optional.of("{}"));
        when(revisionRepository.nextRevisionNumber("proj_1")).thenReturn(99);
    }

    @Test
    void autoMergeNonConflictingChanges() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        EntityRef clipA = new EntityRef(EntityKind.CLIP, "clip_a");
        EntityRef clipB = new EntityRef(EntityKind.CLIP, "clip_b");

        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clipA, "trim clip A");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clipB, "effect on clip B");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(any(), any())).thenReturn(srcDiff).thenReturn(tgtDiff);

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertEquals(MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
        assertTrue(result.conflicts().isEmpty());
        assertNotNull(result.mergeSummary());
    }

    @Test
    void conflictingChangesReturnConflicts() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_shared");

        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim source");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim target");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(any(), any())).thenReturn(srcDiff).thenReturn(tgtDiff);

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertEquals(MergeStatus.CONFLICTS, result.status());
        assertNull(result.mergedRevisionId());
        assertEquals(1, result.conflicts().size());
        assertNotNull(result.mergeSummary());
        assertTrue(result.mergeSummary().conflictCount() > 0);
    }

    @Test
    void resolvedConflictsWithUseSourceShouldMerge() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_shared");

        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "source trim");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "target trim");

        var srcDiff = new SemanticDiffResult("tl", "tl_src", 1, 2, "1.0", List.of(srcChange), false);
        var tgtDiff = new SemanticDiffResult("tl", "tl_tgt", 1, 3, "1.0", List.of(tgtChange), false);

        when(semanticDiffService.diff(any(), any())).thenReturn(srcDiff).thenReturn(tgtDiff);

        Map<String, TimelineResolutionIntent> resolutions = Map.of(
                clip.key(), TimelineResolutionIntent.useSource(clip, TimelineConflictType.SAME_ENTITY_MODIFIED));

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMergeWithResolutions(req, resolutions);

        assertEquals(MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId());
    }

    @Test
    void noOpWhenBothDiffsStructurallyEqual() throws Exception {
        stubRevisions("trev_base", "trev_src", "trev_tgt");

        var noOpDiff = new SemanticDiffResult("tl", "tl", 1, 1, "1.0", List.of(), true);

        when(semanticDiffService.diff(any(), any())).thenReturn(noOpDiff);

        TimelineMergeRequest req = new TimelineMergeRequest(
                "proj_1", "tenant_1", "trev_base", "trev_src", "trev_tgt", null, null);

        TimelineMergeResult result = mergeService.threeWayMerge(req);

        assertEquals(MergeStatus.NO_OP, result.status());
        assertNull(result.mergedRevisionId());
        assertEquals(0, result.mergeSummary().mergedEntities());
    }
}
