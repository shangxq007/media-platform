package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.app.TimelineMutationContext;
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
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.diff.merge.TimelineMergeResult.MergeStatus;
import com.example.platform.timeline.diff.merge.TimelineMergeSummary;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TimelineMergeControllerTest {

    private static final String TENANT_ID = "tenant_1";
    private static final String PROJECT_ID = "proj_1";
    private static final CanonicalActor SERVER_ACTOR = CanonicalActor.user(
            "server-user", TENANT_ID, Set.of(), "test");

    private TimelineRevisionQueryService revisionQueryService;
    private TimelineRevisionDiffQuery revisionDiffQuery;
    private TimelineMergeEngine mergeEngine;
    private TimelineReviewEventPublisher eventPublisher;
    private TimelineProjectAuthorizationService projectAuthorization;
    private TimelineRevisionController controller;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        revisionQueryService = mock(TimelineRevisionQueryService.class);
        revisionDiffQuery = mock(TimelineRevisionDiffQuery.class);
        mergeEngine = mock(TimelineMergeEngine.class);
        eventPublisher = mock(TimelineReviewEventPublisher.class);
        projectAuthorization = mock(TimelineProjectAuthorizationService.class);
        when(projectAuthorization.requireWrite(TENANT_ID, PROJECT_ID)).thenReturn(SERVER_ACTOR);
        controller = new TimelineRevisionController(
                revisionQueryService, revisionDiffQuery, mergeEngine, eventPublisher,
                null, null, null, null, projectAuthorization);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldMergeWithoutConflicts() {
        var req = new TimelineRevisionController.MergeApiRequest(
                "trev_base", "trev_src", "trev_tgt", "Merge test", List.of());

        var result = new TimelineMergeResult(
                MergeStatus.MERGED, "trev_base", "trev_src", "trev_tgt",
                "trev_merge_1", List.of(), List.of(),
                TimelineMergeSummary.merged(2, 1, List.of("CLIP:clip_a", "CLIP:clip_b")),
                "Merge completed", null);

        when(mergeEngine.merge(any(TimelineMergeRequest.class))).thenReturn(result);

        ResponseEntity<TimelineRevisionController.MergeApiResponse> response =
                controller.merge(PROJECT_ID, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("MERGED", response.getBody().status());
        assertEquals("trev_merge_1", response.getBody().mergedRevisionId());
        assertTrue(response.getBody().conflicts().isEmpty());
        assertTrustedMutationContext();
    }

    @Test
    void shouldReturnConflictsWhenConflictDetected() {
        var req = new TimelineRevisionController.MergeApiRequest(
                "trev_base", "trev_src", "trev_tgt", "Merge test", List.of());

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

        when(mergeEngine.merge(any(TimelineMergeRequest.class))).thenReturn(result);

        ResponseEntity<TimelineRevisionController.MergeApiResponse> response =
                controller.merge(PROJECT_ID, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CONFLICTS", response.getBody().status());
        assertNull(response.getBody().mergedRevisionId());
        assertEquals(1, response.getBody().conflicts().size());
        assertTrustedMutationContext();
    }

    private void assertTrustedMutationContext() {
        ArgumentCaptor<TimelineMergeRequest> requestCaptor =
                ArgumentCaptor.forClass(TimelineMergeRequest.class);
        verify(mergeEngine).merge(requestCaptor.capture());
        verify(projectAuthorization).requireWrite(TENANT_ID, PROJECT_ID);

        TimelineMutationContext context = requestCaptor.getValue().mutationContext();
        assertEquals(TENANT_ID, context.tenantId());
        assertEquals(PROJECT_ID, context.projectId());
        assertEquals(SERVER_ACTOR, context.actor());
        assertEquals("server-user", context.authorUserId());
    }
}
