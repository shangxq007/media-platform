package com.example.platform.web.render;

import com.example.platform.timeline.api.review.ReviewRecords;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.timeline.api.review.TimelineComments;
import com.example.platform.timeline.api.review.ReviewQueries;
import com.example.platform.timeline.api.review.TimelineReviews;
import com.example.platform.timeline.api.review.ReviewDecisions;
import com.example.platform.timeline.diff.merge.EntityKind;
import com.example.platform.timeline.diff.merge.EntityRef;
import com.example.platform.timeline.diff.merge.TimelineComment;
import com.example.platform.timeline.diff.merge.ReviewDecision;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TimelineReviewControllerTest {

    private TimelineReviews reviewService;
    private TimelineComments commentService;
    private ReviewDecisions decisionService;
    private TimelineReviewController controller;
    private TimelineProjectAuthorizationService projectAuthorization;

    @BeforeEach
    void setUp() {
        reviewService = mock(TimelineReviews.class);
        commentService = mock(TimelineComments.class);
        decisionService = mock(ReviewDecisions.class);
        projectAuthorization = mock(TimelineProjectAuthorizationService.class);
        var actor = com.example.platform.shared.authorization.CanonicalActor.user(
                "user_1", "tenant_1", Set.of(), "test");
        when(projectAuthorization.requireWrite(any(), any())).thenReturn(actor);
        when(projectAuthorization.requireRead(any(), any())).thenReturn(actor);
        OffsetDateTime now = OffsetDateTime.now();
        when(reviewService.getReview(anyString(), nullable(String.class), anyString())).thenReturn(Optional.of(
                new ReviewRecords.ReviewRow(
                        "rvw_1", "proj_1", "tenant_1", "trev_001", "user_1",
                        "Review Title", "Review Description", "OPEN", now, now)));
        controller = new TimelineReviewController(reviewService, commentService,
                decisionService, projectAuthorization);
    }

    @Test
    void shouldCreateReview() {
        var body = new TimelineReviewController.CreateReviewRequest(
                "trev_001", "Review Title", "Review Description");
        var review = com.example.platform.timeline.diff.merge.TimelineReview.create(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1", "Review Title", "Review Description");

        when(reviewService.createReview(any(), any(), any(), any(), any())).thenReturn(review);
        OffsetDateTime now = OffsetDateTime.now();
        var row = new ReviewRecords.ReviewRow(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1",
                "Review Title", "Review Description", "OPEN", now, now);
        when(reviewService.getReview("proj_1", null, "rvw_001")).thenReturn(Optional.of(row));

        ResponseEntity<TimelineReviewController.ReviewResponse> response =
                controller.createReview("proj_1", body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("rvw_001", response.getBody().reviewId());
    }

    @Test
    void frameCommentUsesAuthenticatedActorAndBindsTheExactAnchor() {
        var anchor = new com.example.platform.timeline.api.review.ReviewFrameAnchor(
                com.example.platform.timeline.api.review.ReviewFrameAnchor.Side.B, "alternate", "73/24");
        var request = UUID.randomUUID();
        var body = new TimelineReviewController.AddCommentRequest(null, "trev_001", null, null, "note", anchor, request);
        var comment = new ReviewRecords.CommentRow("comment", "rvw_1", null, "trev_001", null,
                "user_1", "note", OffsetDateTime.now(), anchor);
        when(commentService.addFrameComment("proj_1", null, "trev_001", "user_1", "note", anchor, request))
                .thenReturn(comment);
        var response = controller.addFrameComment("proj_1", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(anchor, response.getBody().frameAnchor());
        assertEquals("trev_001", response.getBody().revisionId());
        verify(projectAuthorization).requireWrite(null, "proj_1");
    }

    @Test
    void rejectsAnUnownedReviewBeforePostingAFrameComment() {
        var anchor = new com.example.platform.timeline.api.review.ReviewFrameAnchor(
                com.example.platform.timeline.api.review.ReviewFrameAnchor.Side.A, "main", "3/1");
        when(reviewService.getReview("proj_1", null, "missing")).thenReturn(Optional.empty());
        var body = new TimelineReviewController.AddCommentRequest(null, "trev_001", null, null, "note", anchor, UUID.randomUUID());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.addComment("proj_1", "missing", body));
        verifyNoInteractions(commentService);
    }

    @Test
    void rejectsNegativeFrameAnchorAsABadRequestBeforePosting() throws Exception {
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/render/projects/proj_1/timeline/reviews/comments")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"revisionId":"trev_001","content":"note",
                                 "requestId":"22222222-2222-4222-8222-222222222222",
                                 "frameAnchor":{"side":"B","branch":"alternate","position":"-1/24"}}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(commentService);
    }

    @Test
    void shouldApproveReview() {
        ResponseEntity<Map<String, Object>> response =
                controller.approve("proj_1", "rvw_1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("APPROVED", response.getBody().get("status"));
    }

    @Test
    void shouldCheckMergeGuard() {
        when(reviewService.checkMergeGuard("rvw_1"))
                .thenReturn(new TimelineReviews.MergeGuardResult(false, "Review is OPEN"));

        ResponseEntity<Map<String, Object>> response =
                controller.checkMergeGuard("proj_1", "rvw_1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().get("canMerge"));
    }

    @Test
    void resolveRejectsThreadOutsideOwnedReviewBeforeMutation() {
        when(commentService.resolveThread("rvw_1", "thread_foreign")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.resolveThread("proj_1", "rvw_1", "thread_foreign"));

        verify(commentService).resolveThread("rvw_1", "thread_foreign");
        verifyNoMoreInteractions(commentService);
    }

    @Test
    void reopenRejectsThreadOutsideOwnedReviewBeforeMutation() {
        when(commentService.reopenThread("rvw_1", "thread_foreign")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.reopenThread("proj_1", "rvw_1", "thread_foreign"));

        verify(commentService).reopenThread("rvw_1", "thread_foreign");
    }
}
