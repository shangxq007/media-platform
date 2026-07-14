package com.example.platform.web.render;

import com.example.platform.render.api.RenderController;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
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
        controller = new RenderController(renderJobService);
    }

    @Test
    void shouldCreateJob() {
        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED");
        when(renderJobService.create(request)).thenReturn(expected);

        RenderJobResponse response = controller.create(request);

        assertNotNull(response);
        assertEquals("rj-1", response.id());
        assertEquals("QUEUED", response.status());
        verify(renderJobService).create(request);
    }

    @Test
    void shouldGetJob() {
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED");
        when(renderJobService.getById("rj-1")).thenReturn(expected);

        RenderJobResponse response = controller.getJob("rj-1");

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        verify(renderJobService).getById("rj-1");
    }

    @Test
    void shouldListJobs() {
        when(renderJobService.list()).thenReturn(List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED")));

        List<RenderJobResponse> response = controller.list();

        assertEquals(1, response.size());
        assertEquals("rj-1", response.get(0).id());
    }

    @Test
    void shouldCancelJob() {
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "CANCELLED");
        when(renderJobService.cancel("rj-1", "tenant-1")).thenReturn(expected);

        RenderJobResponse response = controller.cancelJob("rj-1", "tenant-1");

        assertNotNull(response);
        assertEquals("CANCELLED", response.status());
        verify(renderJobService).cancel("rj-1", "tenant-1");
    }

    @Test
    void shouldRetryJob() {
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED");
        when(renderJobService.retry("rj-1", "tenant-1")).thenReturn(expected);

        RenderJobResponse response = controller.retryJob("rj-1", "tenant-1");

        assertNotNull(response);
        assertEquals("QUEUED", response.status());
        verify(renderJobService).retry("rj-1", "tenant-1");
    }

    @Test
    void shouldCreateJobWithDefaultProfile() {
        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "");
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "", "QUEUED");
        when(renderJobService.create(request)).thenReturn(expected);

        RenderJobResponse response = controller.create(request);

        assertNotNull(response);
        assertEquals("rj-1", response.id());
    }
}
