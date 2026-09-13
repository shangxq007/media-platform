package com.example.platform.web.render;

import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.timeline.api.review.TimelineComments;
import com.example.platform.timeline.api.review.ReviewQueries;
import com.example.platform.timeline.api.review.TimelineReviews;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineWorkbenchControllerTest {

    private TimelineRevisionQueryService revisions;
    private ReviewQueries reviews;
    private TimelineReviews reviewService;
    private TimelineComments comments;
    private TimelineProjectAuthorizationService authorization;
    private TimelineWorkbenchController controller;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        revisions = mock(TimelineRevisionQueryService.class);
        reviews = mock(ReviewQueries.class);
        reviewService = mock(TimelineReviews.class);
        comments = mock(TimelineComments.class);
        authorization = mock(TimelineProjectAuthorizationService.class);
        controller = new TimelineWorkbenchController(
                revisions,
                mock(TimelineRevisionDiffQuery.class),
                reviewService,
                reviews,
                comments,
                authorization);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void detailRejectsForeignProjectReviewBeforeCommentsOrThreadsDisclosure() {
        when(reviews.findOwnedById("review-b", "project-a", "tenant-a"))
                .thenReturn(Optional.empty());

        var response = controller.reviewDetail("project-a", "timeline-a", "review-b");

        assertEquals(404, response.getStatusCode().value());
        verify(authorization).requireRead("tenant-a", "project-a");
        verify(reviews).findOwnedById("review-b", "project-a", "tenant-a");
        verifyNoInteractions(comments, reviewService);
        org.mockito.Mockito.verifyNoMoreInteractions(reviews);
    }

    @Test
    void workspaceListsOnlyExplicitAuthorizedTenantAndProject() {
        when(revisions.listFacets("project-a", "tenant-a"))
                .thenReturn(new TimelineRevisionQueries.RevisionFacets(List.of(), List.of()));
        when(reviews.listOwnedByProject("project-a", "tenant-a", 20)).thenReturn(List.of());

        var response = controller.workbench("project-a", "timeline-a");

        assertEquals(200, response.getStatusCode().value());
        verify(authorization).requireRead("tenant-a", "project-a");
        verify(reviews).listOwnedByProject("project-a", "tenant-a", 20);
    }
}
