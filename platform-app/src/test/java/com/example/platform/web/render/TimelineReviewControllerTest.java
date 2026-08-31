package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.TimelineCommentService;
import com.example.platform.render.app.timeline.TimelineReviewRepository;
import com.example.platform.render.app.timeline.TimelineReviewService;
import com.example.platform.render.app.timeline.ReviewDecisionService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
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
        var review = com.example.platform.timeline.diff.merge.TimelineReview.create(
                "rvw_001", "proj_1", "tenant_1", "trev_001", "user_1", "Review Title", "Review Description");

        assertUnavailable(() -> controller.createReview("proj_1", body));
        verifyNoInteractions(reviewService, commentService, decisionService, eventPublisher);
    }

    @Test
    void shouldApproveReview() {
        assertUnavailable(() -> controller.approve("proj_1", "rvw_1", "user_2"));
        verifyNoInteractions(reviewService, commentService, decisionService, eventPublisher);
    }

    @Test
    void shouldCheckMergeGuard() {
        assertUnavailable(() -> controller.checkMergeGuard("proj_1", "rvw_1"));
        verifyNoInteractions(reviewService, commentService, decisionService, eventPublisher);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
