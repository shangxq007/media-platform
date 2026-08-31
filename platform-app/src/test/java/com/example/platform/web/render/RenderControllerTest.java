package com.example.platform.web.render;

import com.example.platform.render.api.RenderController;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderControllerTest {

    private RenderJobService renderJobService;
    private RenderController controller;

    @BeforeEach
    void setUp() {
        renderJobService = mock(RenderJobService.class);
        controller = new RenderController(renderJobService, null, java.util.List.of(),
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldCreateJob() {
        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");
        assertUnavailable(() -> controller.createRenderJob("tenant-1", "proj-1", request));
        verifyNoInteractions(renderJobService);
    }

    @Test
    void shouldGetJob() {
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED");
        when(renderJobService.getByIdAndProject("tenant-1", "proj-1", "rj-1")).thenReturn(expected);

        RenderJobResponse response = controller.getRenderJob("tenant-1", "proj-1", "rj-1");

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        verify(renderJobService).getByIdAndProject("tenant-1", "proj-1", "rj-1");
    }

    @Test
    void shouldListJobs() {
        when(renderJobService.listByProject("tenant-1", "proj-1")).thenReturn(List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED")));

        List<RenderJobResponse> response = controller.listRenderJobs("tenant-1", "proj-1");

        assertEquals(1, response.size());
        assertEquals("rj-1", response.get(0).id());
    }

    @Test
    void shouldCancelJob() {
        assertUnavailable(() -> controller.cancelJob("rj-1", "tenant-1"));
        verifyNoInteractions(renderJobService);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
