package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.render.domain.planning.*; // render-kept internal types only
import com.example.platform.timeline.diff.merge.EntityKind;
import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.SemanticChange;
import com.example.platform.timeline.diff.merge.SemanticChangeType;
import com.example.platform.timeline.diff.merge.TimelineConflict;
import com.example.platform.timeline.diff.merge.TimelineConflictType;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.TimelineMergeResult.MergeStatus;
import com.example.platform.timeline.diff.merge.TimelineMergeSummary;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TimelineMergeControllerTest {

    private TimelineRevisionQueryService revisionQueryService;
    private TimelineRevisionDiffQuery revisionDiffQuery;
    private TimelineMergeEngine mergeEngine;
    private TimelineReviewEventPublisher eventPublisher;
    private TimelineRevisionController controller;

    @BeforeEach
    void setUp() {
        revisionQueryService = mock(TimelineRevisionQueryService.class);
        revisionDiffQuery = mock(TimelineRevisionDiffQuery.class);
        mergeEngine = mock(TimelineMergeEngine.class);
        eventPublisher = mock(TimelineReviewEventPublisher.class);
        controller = new TimelineRevisionController(
                revisionQueryService, revisionDiffQuery, mergeEngine, eventPublisher,
                null, null, null, null, null);
    }

    @Test
    void shouldMergeWithoutConflicts() {
        var req = new TimelineRevisionController.MergeApiRequest(
                "tenant_1", "trev_base", "trev_src", "trev_tgt", "user_1", "Merge test", List.of());

        var result = new TimelineMergeResult(
                MergeStatus.MERGED, "trev_base", "trev_src", "trev_tgt",
                "trev_merge_1", List.of(), List.of(),
                TimelineMergeSummary.merged(2, 1, List.of("CLIP:clip_a", "CLIP:clip_b")),
                "Merge completed", null);

        when(mergeEngine.merge(any())).thenReturn(result);

        ResponseEntity<TimelineRevisionController.MergeApiResponse> response =
                controller.merge("proj_1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("MERGED", response.getBody().status());
        assertEquals("trev_merge_1", response.getBody().mergedRevisionId());
        assertTrue(response.getBody().conflicts().isEmpty());
    }

    @Test
    void shouldReturnConflictsWhenConflictDetected() {
        var req = new TimelineRevisionController.MergeApiRequest(
                "tenant_1", "trev_base", "trev_src", "trev_tgt", "user_1", "Merge test", List.of());

        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_shared");
        var srcChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "source change");
        var tgtChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "target change");
        var conflict = TimelineConflict.of(clip, TimelineConflictType.SAME_ENTITY_MODIFIED,
                srcChange, tgtChange, "conflict");

        var result = new TimelineMergeResult(
                MergeStatus.CONFLICTS, "trev_base", "trev_src", "trev_tgt",
                null, List.of(), List.of(conflict),
                TimelineMergeSummary.conflicts(0, 0, List.of(), List.of("CLIP:clip_shared")),
                "Conflict detected", null);

        when(mergeEngine.merge(any())).thenReturn(result);

        ResponseEntity<TimelineRevisionController.MergeApiResponse> response =
                controller.merge("proj_1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CONFLICTS", response.getBody().status());
        assertNull(response.getBody().mergedRevisionId());
        assertEquals(1, response.getBody().conflicts().size());
    }
}
