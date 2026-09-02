package com.example.platform.web.render;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TimelineControllerAuthorizationPreparationTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void currentControllerRejectsBeforeHistoryDisclosure() {
        TenantContext.set("tenant-1");
        TimelineRevisionQueryService query = mock(TimelineRevisionQueryService.class);
        TimelineProjectAuthorizationService authorization = mock(TimelineProjectAuthorizationService.class);
        when(authorization.requireRead("tenant-1", "project-1"))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "denied"));
        TimelineRevisionController controller = new TimelineRevisionController(
                query, mock(TimelineRevisionDiffQuery.class), null,
                mock(com.example.platform.render.app.event.TimelineReviewEventPublisher.class),
                null, null, null, null, authorization);

        assertThrows(ResponseStatusException.class,
                () -> controller.list("project-1", 30, null, null, null));
        verify(query, never()).listHistory(
                "project-1", "tenant-1", null, null, null, 30);
    }

    @Test
    void legacyControllerRejectsBeforeHeadDisclosure() {
        TenantContext.set("tenant-1");
        TimelineRevisionQueryService query = mock(TimelineRevisionQueryService.class);
        TimelineProjectAuthorizationService authorization = mock(TimelineProjectAuthorizationService.class);
        when(authorization.requireRead("tenant-1", "project-1"))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "denied"));
        TimelineGitV1Controller controller = new TimelineGitV1Controller(
                mock(com.example.platform.timeline.app.TimelineRevisionSaveService.class),
                query,
                mock(com.example.platform.render.app.timeline.RenderJobRevisionPinningService.class),
                new com.example.platform.timeline.canonical.TimelineContentDigester(),
                mock(com.example.platform.timeline.app.TimelineRevisionDiffQuery.class),
                mock(com.example.platform.timeline.app.TimelinePatchApplicationService.class),
                authorization,
                java.util.Optional::empty);

        assertThrows(ResponseStatusException.class,
                () -> controller.getCurrentRevision("project-1"));
        verify(query, never()).findHead("project-1", "tenant-1");
    }
}
