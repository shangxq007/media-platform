package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.TimelineCommentService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
import com.example.platform.render.app.timeline.ReviewDecisionService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
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

    private TimelineReviewService reviewService;
    private TimelineCommentService commentService;
    private ReviewDecisionService decisionService;
    private TimelineReviewEventPublisher eventPublisher;
    private TimelineReviewController controller;
    private TimelineProjectAuthorizationService projectAuthorization;

    @BeforeEach
    void setUp() {
        reviewService = mock(TimelineReviewService.class);
        commentService = mock(TimelineCommentService.class);
        decisionService = mock(ReviewDecisionService.class);
        eventPublisher = mock(TimelineReviewEventPublisher.class);
        projectAuthorization = mock(TimelineProjectAuthorizationService.class);
        var actor = com.example.platform.shared.authorization.CanonicalActor.user(
                "user_1", "tenant_1", Set.of(), "test");
        when(projectAuthorization.requireWrite(any(), any())).thenReturn(actor);
        when(projectAuthorization.requireRead(any(), any())).thenReturn(actor);
        OffsetDateTime now = OffsetDateTime.now();
        when(reviewService.getReview(anyString(), nullable(String.class), anyString())).thenReturn(Optional.of(
                new TimelineReviewRepository.ReviewRow(
                        "rvw_1", "proj_1", "tenant_1", "trev_001", "user_1",
                        "Review Title", "Review Description", "OPEN", now, now)));
        controller = new TimelineReviewController(reviewService, commentService,
                decisionService, eventPublisher, projectAuthorization);
    }

    @Test
    void shouldCreateReview() {
        var body = new TimelineReviewController.CreateReviewRequest(
                "trev_001", "Review Title", "Review Description");
        var review = com.example.platform.timeline.diff.merge.TimelineReview.create(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1", "Review Title", "Review Description");

        when(reviewService.createReview(any(), any(), any(), any(), any())).thenReturn(review);
        OffsetDateTime now = OffsetDateTime.now();
        var row = new TimelineReviewRepository.ReviewRow(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1",
                "Review Title", "Review Description", "OPEN", now, now);
        when(reviewService.getReview("proj_1", null, "rvw_001")).thenReturn(Optional.of(row));

        ResponseEntity<TimelineReviewController.ReviewResponse> response =
                controller.createReview("proj_1", body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("rvw_001", response.getBody().reviewId());
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
                .thenReturn(new TimelineReviewService.MergeGuardResult(false, "Review is OPEN"));

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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reopenRejectsThreadOutsideOwnedReviewBeforeMutation() {
        when(commentService.reopenThread("rvw_1", "thread_foreign")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.reopenThread("proj_1", "rvw_1", "thread_foreign"));

        verify(commentService).reopenThread("rvw_1", "thread_foreign");
    }
}
