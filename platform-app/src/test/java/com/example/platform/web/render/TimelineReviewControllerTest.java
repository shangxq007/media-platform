package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.TimelineCommentService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
import com.example.platform.render.app.timeline.ReviewDecisionService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.TimelineComment;
import com.example.platform.render.domain.timeline.internal.ReviewDecision;
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

    @BeforeEach
    void setUp() {
        reviewService = mock(TimelineReviewService.class);
        commentService = mock(TimelineCommentService.class);
        decisionService = mock(ReviewDecisionService.class);
        eventPublisher = mock(TimelineReviewEventPublisher.class);
        controller = new TimelineReviewController(reviewService, commentService,
                decisionService, eventPublisher);
    }

    @Test
    void shouldCreateReview() {
        var body = new TimelineReviewController.CreateReviewRequest(
                "trev_001", "user_1", "Review Title", "Review Description");
        var review = com.example.platform.render.domain.timeline.internal.TimelineReview.create(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1", "Review Title", "Review Description");

        when(reviewService.createReview(any(), any(), any(), any(), any())).thenReturn(review);
        OffsetDateTime now = OffsetDateTime.now();
        var row = new TimelineReviewRepository.ReviewRow(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1",
                "Review Title", "Review Description", "OPEN", now, now);
        when(reviewService.getReview("rvw_001")).thenReturn(Optional.of(row));

        ResponseEntity<TimelineReviewController.ReviewResponse> response =
                controller.createReview("proj_1", body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("rvw_001", response.getBody().reviewId());
    }

    @Test
    void shouldApproveReview() {
        ResponseEntity<Map<String, Object>> response =
                controller.approve("proj_1", "rvw_1", "user_2");

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
}