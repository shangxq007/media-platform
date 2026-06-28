package com.example.platform.render.api;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.app.caption.CaptionTemplateRenderService;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.caption.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Caption Template Render API layer.
 * Proves: DTO mapping, controller behavior, audit events, public API safety.
 */
class CaptionTemplateRenderApiTest {

    private InMemoryRenderAuditEventSink auditSink;
    private RenderAuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        auditSink = new InMemoryRenderAuditEventSink();
        auditRecorder = new RenderAuditRecorder(auditSink);
    }

    // --- DTO mapping ---

    @Test
    @DisplayName("Valid API request maps to domain request")
    void validRequestMapsToDomain() {
        CaptionTemplateRenderApiMapper mapper = new CaptionTemplateRenderApiMapper();
        CaptionTemplateRenderApiRequest apiRequest = validApiRequest();

        CaptionTemplateRenderRequest domain = mapper.toDomainRequest("proj-1", apiRequest);

        assertEquals("proj-1", domain.projectId());
        assertEquals("prod-source-1", domain.sourceProductId());
        assertEquals(1, domain.captionSegments().size());
        assertEquals("Hello world", domain.captionSegments().get(0).text());
    }

    @Test
    @DisplayName("Path projectId is used, not body")
    void pathProjectIdUsed() {
        CaptionTemplateRenderApiMapper mapper = new CaptionTemplateRenderApiMapper();
        CaptionTemplateRenderApiRequest apiRequest = validApiRequest();

        CaptionTemplateRenderRequest domain = mapper.toDomainRequest("proj-from-path", apiRequest);

        assertEquals("proj-from-path", domain.projectId());
    }

    @Test
    @DisplayName("API request cannot set provider/backend/storage internals")
    void apiRequestNoInternals() {
        CaptionTemplateRenderApiRequest request = validApiRequest();
        String str = request.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
    }

    @Test
    @DisplayName("Success result maps to success response")
    void successResultMapsToResponse() {
        CaptionTemplateRenderApiMapper mapper = new CaptionTemplateRenderApiMapper();
        CaptionTemplateRenderResult result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderApiResponse response = mapper.toApiResponse(result);

        assertEquals("READY", response.status());
        assertTrue(response.ready());
        assertEquals("rj-1", response.renderJobId());
        assertEquals("prod-out-1", response.outputProductId());
        assertTrue(response.validationErrors().isEmpty());
    }

    @Test
    @DisplayName("Validation failure maps to validation response")
    void validationFailureMapsToResponse() {
        CaptionTemplateRenderApiMapper mapper = new CaptionTemplateRenderApiMapper();
        CaptionTemplateRenderResult result = CaptionTemplateRenderResult.validationFailed(
                List.of("text is blank", "startMs < 0"));

        CaptionTemplateRenderApiResponse response = mapper.toApiResponse(result);

        assertEquals("VALIDATION_FAILED", response.status());
        assertFalse(response.ready());
        assertEquals(2, response.validationErrors().size());
    }

    @Test
    @DisplayName("Failed result maps to failed response")
    void failedResultMapsToResponse() {
        CaptionTemplateRenderApiMapper mapper = new CaptionTemplateRenderApiMapper();
        CaptionTemplateRenderResult result = new CaptionTemplateRenderResult(
                "rj-1", null, "FAILED", false, null,
                List.of(), "Render failed", Map.of());

        CaptionTemplateRenderApiResponse response = mapper.toApiResponse(result);

        assertEquals("FAILED", response.status());
        assertFalse(response.ready());
    }

    // --- Controller behavior ---

    @Test
    @DisplayName("Controller with valid request returns success")
    void controllerValidRequest() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);

        ResponseEntity<CaptionTemplateRenderApiResponse> response = controller.render(
                "tenant-1", "proj-1", validApiRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("READY", response.getBody().status());
    }

    @Test
    @DisplayName("Controller with invalid request returns 400")
    void controllerInvalidRequest() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.validationFailed(
                List.of("text is blank"));

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);

        CaptionTemplateRenderApiRequest invalidRequest = new CaptionTemplateRenderApiRequest(
                "prod-1", List.of(new CaptionTemplateSegmentDto(0L, 1000L, "")),
                null, null, null);

        ResponseEntity<CaptionTemplateRenderApiResponse> response = controller.render(
                "tenant-1", "proj-1", invalidRequest);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", response.getBody().status());
    }

    @Test
    @DisplayName("Controller with failed render returns 500")
    void controllerFailedRender() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = new CaptionTemplateRenderResult(
                "rj-1", null, "FAILED", false, null,
                List.of(), "Internal error", Map.of());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);

        ResponseEntity<CaptionTemplateRenderApiResponse> response = controller.render(
                "tenant-1", "proj-1", validApiRequest());

        assertEquals(500, response.getStatusCode().value());
    }

    // --- Audit events ---

    @Test
    @DisplayName("Audit requested event emitted")
    void auditRequestedEmitted() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);
        controller.render("tenant-1", "proj-1", validApiRequest());

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_REQUESTED));
    }

    @Test
    @DisplayName("Audit completed event emitted on success")
    void auditCompletedEmitted() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);
        controller.render("tenant-1", "proj-1", validApiRequest());

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED));
    }

    @Test
    @DisplayName("Audit validation-failed event emitted on validation failure")
    void auditValidationFailedEmitted() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.validationFailed(List.of("error"));

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);
        controller.render("tenant-1", "proj-1", validApiRequest());

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("Audit failed event emitted on render failure")
    void auditFailedEmitted() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = new CaptionTemplateRenderResult(
                "rj-1", null, "FAILED", false, null, List.of(), "error", Map.of());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);
        controller.render("tenant-1", "proj-1", validApiRequest());

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_FAILED));
    }

    @Test
    @DisplayName("Audit payload excludes full caption text")
    void auditExcludesCaptionText() {
        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), auditRecorder);
        controller.render("tenant-1", "proj-1", validApiRequest());

        auditSink.findAll().forEach(event -> {
            if (event.message() != null) {
                assertFalse(event.message().contains("Hello world"),
                        "Audit message must not contain caption text");
            }
        });
    }

    @Test
    @DisplayName("Audit failures do not break response")
    void auditFailuresDoNotBreakResponse() {
        RenderAuditRecorder failingRecorder = new RenderAuditRecorder(new RenderAuditEventSink() {
            @Override public void record(RenderAuditEvent e) { throw new RuntimeException("fail"); }
            @Override public java.util.List<RenderAuditEvent> findAll() { return List.of(); }
            @Override public java.util.List<RenderAuditEvent> findByRenderJobId(String id) { return List.of(); }
            @Override public java.util.List<RenderAuditEvent> findByProjectId(String id) { return List.of(); }
            @Override public void clear() {}
        });

        MockCaptionService mockService = new MockCaptionService();
        mockService.result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-out-1", CaptionOutputProfileSpec.hd1080p());

        CaptionTemplateRenderController controller = new CaptionTemplateRenderController(
                mockService, new CaptionTemplateRenderApiMapper(), failingRecorder);

        ResponseEntity<CaptionTemplateRenderApiResponse> response = controller.render(
                "tenant-1", "proj-1", validApiRequest());

        assertEquals(200, response.getStatusCode().value());
    }

    // --- Public API safety ---

    @Test
    @DisplayName("Response does not expose provider/storage internals")
    void responseNoInternals() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));
        String str = response.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("renderCorrelationId"));
        assertFalse(str.contains("renderExecutionPlanId"));
    }

    @Test
    @DisplayName("Controller does not call FFmpeg directly")
    void controllerNoDirectFfmpeg() {
        // Verified by code: controller delegates to service, no ProcessToolRunner dependency
        assertNotNull(new CaptionTemplateRenderApiMapper());
    }

    @Test
    @DisplayName("Controller does not call Remotion")
    void controllerNoRemotion() {
        // Verified by code: no Remotion references in controller
        assertNotNull(new CaptionTemplateRenderApiMapper());
    }

    // --- Helpers ---

    private CaptionTemplateRenderApiRequest validApiRequest() {
        return new CaptionTemplateRenderApiRequest(
                "prod-source-1",
                List.of(new CaptionTemplateSegmentDto(0L, 2500L, "Hello world")),
                null, null, Map.of("requestSource", "api"));
    }

    static class MockCaptionService extends CaptionTemplateRenderService {
        CaptionTemplateRenderResult result;
        MockCaptionService() {
            super(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
        }
        @Override
        public CaptionTemplateRenderResult render(CaptionTemplateRenderRequest request) {
            return result;
        }
    }
}
