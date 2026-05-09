package com.example.platform.render.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderControllerTest {

    private RenderJobService service;
    private RenderController controller;

    @BeforeEach
    void setUp() {
        service = mock(RenderJobService.class);
        controller = new RenderController(service);
    }

    @Test
    void createDelegatesToService() {
        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "social_1080p");
        RenderJobResponse expected = new RenderJobResponse("rj_abc", "proj-1", "snap-1", "social_1080p", "QUEUED");
        when(service.create(request)).thenReturn(expected);

        RenderJobResponse response = controller.create(request);

        assertNotNull(response);
        assertEquals("rj_abc", response.id());
        assertEquals("QUEUED", response.status());
    }

    @Test
    void listDelegatesToService() {
        List<RenderJobResponse> expected = List.of(
                new RenderJobResponse("rj_1", "proj-1", "snap-1", "social_1080p", "QUEUED"),
                new RenderJobResponse("rj_2", "proj-2", "snap-2", "standard", "COMPLETED")
        );
        when(service.list()).thenReturn(expected);

        List<RenderJobResponse> response = controller.list();

        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals("rj_1", response.get(0).id());
        assertEquals("rj_2", response.get(1).id());
    }

    @Test
    void listReturnsEmptyWhenServiceReturnsEmpty() {
        when(service.list()).thenReturn(List.of());

        List<RenderJobResponse> response = controller.list();
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }
}
