package com.example.platform.render.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderControllerTest {

    private RenderJobService service;
    private RenderController controller;

    @BeforeEach
    void setUp() {
        service = mock(RenderJobService.class);
        controller = new RenderController(service, null, java.util.List.of(),
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void createDelegatesToService() {
        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "social_1080p");

        AuthorizationDeniedException failure = assertThrowsExactly(AuthorizationDeniedException.class,
                () -> controller.createRenderJob("tenant-1", "proj-1", request));

        assertAuthorizationUnavailable(failure, "render job creation");
        verifyNoInteractions(service);
    }

    @Test
    void listDelegatesToService() {
        List<RenderJobResponse> expected = List.of(
                new RenderJobResponse("rj_1", "proj-1", "snap-1", "social_1080p", "QUEUED"),
                new RenderJobResponse("rj_2", "proj-2", "snap-2", "standard", "COMPLETED")
        );
        when(service.listByProject("tenant-1", "proj-1")).thenReturn(expected);

        List<RenderJobResponse> response = controller.listRenderJobs("tenant-1", "proj-1");

        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals("rj_1", response.get(0).id());
        assertEquals("rj_2", response.get(1).id());
    }

    @Test
    void listReturnsEmptyWhenServiceReturnsEmpty() {
        when(service.listByProject("tenant-1", "proj-1")).thenReturn(List.of());

        List<RenderJobResponse> response = controller.listRenderJobs("tenant-1", "proj-1");
        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    private static void assertAuthorizationUnavailable(
            AuthorizationDeniedException failure, String operation) {
        assertFalse(failure.decision().allowed());
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        assertEquals("FAIL_CLOSED_CONTAINMENT", failure.decision().ruleRef());
        assertEquals(operation + " is unavailable until canonical authorization is established",
                failure.decision().detail());
    }
}
