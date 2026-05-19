package com.example.platform.web.render;

import com.example.platform.identity.app.IdentityAccessService;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.shared.audit.AuditPort;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenderControllerTest {

    private RenderJobService renderJobService;
    private IdentityAccessService identityAccessService;
    private AuditPort auditPort;
    private RenderController controller;

    @BeforeEach
    void setUp() {
        renderJobService = mock(RenderJobService.class);
        identityAccessService = mock(IdentityAccessService.class);
        auditPort = mock(AuditPort.class);
        controller = new RenderController(renderJobService, identityAccessService, auditPort);
    }

    @Test
    void shouldCreateJobFromWebPath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.subject")).thenReturn("web-user");
        when(req.getAttribute("jwt.tenantId")).thenReturn("tenant-1");

        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED");
        when(renderJobService.create(any())).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.submitRenderJob(request, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("rj-1", response.getBody().id());
        verify(renderJobService).create(request);
        verify(auditPort).record(eq("web-user"), eq("RENDER_JOB_SUBMITTED"), eq("RENDER"),
                eq("render_job"), eq("proj-1"), any());
    }

    @Test
    void shouldCreateJobFromMcpPath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/mcp/render/jobs");
        when(req.getHeader("X-API-Key")).thenReturn("api-key-123");
        when(identityAccessService.tenantIdOf("api-key-123")).thenReturn("tenant-2");
        when(identityAccessService.principalOf("api-key-123")).thenReturn("service-account");

        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-2", "snap-2", "4k");
        RenderJobResponse expected = new RenderJobResponse("rj-2", "proj-2", "snap-2", "4k", "QUEUED");
        when(renderJobService.create(any())).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.submitRenderJob(request, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("rj-2", response.getBody().id());
        verify(auditPort).record(eq("service-account"), eq("RENDER_JOB_SUBMITTED"), eq("RENDER"),
                eq("render_job"), eq("proj-2"), any());
    }

    @Test
    void shouldGetJob() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");

        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED");
        when(renderJobService.getById("rj-1")).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.getRenderJob("rj-1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("COMPLETED", response.getBody().status());
    }

    @Test
    void shouldListJobs() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");

        when(renderJobService.list()).thenReturn(java.util.List.of(
                new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED")));

        ResponseEntity<java.util.List<RenderJobResponse>> response = controller.listRenderJobs(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldCancelJobFromWeb() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.tenantId")).thenReturn("tenant-1");

        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "CANCELLED");
        when(renderJobService.cancel("rj-1", "tenant-1")).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.cancelRenderJob("rj-1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", response.getBody().status());
    }

    @Test
    void shouldCancelJobFromMcp() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/mcp/render/jobs");
        when(req.getHeader("X-API-Key")).thenReturn("api-key-123");
        when(identityAccessService.tenantIdOf("api-key-123")).thenReturn("tenant-2");

        RenderJobResponse expected = new RenderJobResponse("rj-2", "proj-2", "snap-2", "4k", "CANCELLED");
        when(renderJobService.cancel("rj-2", "tenant-2")).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.cancelRenderJob("rj-2", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("CANCELLED", response.getBody().status());
    }

    @Test
    void shouldRetryJob() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.tenantId")).thenReturn("tenant-1");

        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED");
        when(renderJobService.retry("rj-1", "tenant-1")).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.retryRenderJob("rj-1", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("QUEUED", response.getBody().status());
    }

    @Test
    void shouldRejectWebRequestWithoutProfile() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.subject")).thenReturn("web-user");

        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "");

        assertThrows(IllegalArgumentException.class, () -> controller.submitRenderJob(request, req));
        verify(renderJobService, never()).create(any());
    }

    @Test
    void shouldAllowMcpRequestWithoutProfile() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/mcp/render/jobs");
        when(req.getHeader("X-API-Key")).thenReturn("api-key-123");
        when(identityAccessService.tenantIdOf("api-key-123")).thenReturn("tenant-2");
        when(identityAccessService.principalOf("api-key-123")).thenReturn("service-account");

        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "snap-1", "");
        RenderJobResponse expected = new RenderJobResponse("rj-1", "proj-1", "snap-1", "", "QUEUED");
        when(renderJobService.create(any())).thenReturn(expected);

        ResponseEntity<RenderJobResponse> response = controller.submitRenderJob(request, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(renderJobService).create(request);
    }

    @Test
    void shouldRejectRequestWithoutProjectId() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.subject")).thenReturn("web-user");

        CreateRenderJobRequest request = new CreateRenderJobRequest("", "snap-1", "default_1080p");

        assertThrows(IllegalArgumentException.class, () -> controller.submitRenderJob(request, req));
    }

    @Test
    void shouldRejectRequestWithoutTimelineSnapshotId() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/render/jobs");
        when(req.getAttribute("jwt.subject")).thenReturn("web-user");

        CreateRenderJobRequest request = new CreateRenderJobRequest("proj-1", "", "default_1080p");

        assertThrows(IllegalArgumentException.class, () -> controller.submitRenderJob(request, req));
    }
}
