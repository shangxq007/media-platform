package com.example.platform.web.render;

import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.render.app.timeline.RenderJobStatusService;
import com.example.platform.render.app.timeline.TimelineMergeService;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.render.app.timeline.TimelineRevisionService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Web/API contract tests for R7 render job status and result endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>Status endpoint success — 200 OK with safe fields</li>
 *   <li>Result endpoint success — 200 OK with safe fields</li>
 *   <li>Not found — unknown renderJobId returns 404</li>
 *   <li>Not found — project mismatch returns 404</li>
 *   <li>Not found — revision mismatch returns 404</li>
 *   <li>Service unavailable — returns 503</li>
 *   <li>No sensitive data in responses</li>
 * </ul>
 */
class TimelineRevisionRenderJobStatusControllerTest {

    private TimelineRevisionService revisionService;
    private TimelineMergeService mergeService;
    private TimelineReviewEventPublisher eventPublisher;
    private TimelineRevisionRenderService renderService;
    private RenderJobStatusService renderJobStatusService;
    private TimelineRevisionController controller;

    @BeforeEach
    void setUp() {
        revisionService = mock(TimelineRevisionService.class);
        mergeService = mock(TimelineMergeService.class);
        eventPublisher = mock(TimelineReviewEventPublisher.class);
        renderService = mock(TimelineRevisionRenderService.class);
        renderJobStatusService = mock(RenderJobStatusService.class);
        controller = new TimelineRevisionController(
                revisionService, mergeService, eventPublisher, renderService, renderJobStatusService);
    }

    // ─── Status endpoint tests ───

    @Test
    @DisplayName("Status endpoint: READY render returns 200 OK with safe fields")
    void statusEndpointReadyRender() {
        // Arrange
        RenderJobStatusResponse expected = new RenderJobStatusResponse(
                "rj_001", "prj_1", "rev_1", "snap_1",
                "READY", "timeline-revision-render", "default_1080p", "mp4",
                "prod_001", "READY",
                List.of("input_1"), 1,
                "2026-06-27T10:00:00Z", "2026-06-27T10:00:05Z",
                "Render completed successfully", true);
        when(renderJobStatusService.findStatus("prj_1", "rev_1", "rj_001"))
                .thenReturn(Optional.of(expected));

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controller.getRenderJobStatus("prj_1", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        RenderJobStatusResponse body = response.getBody();
        assertEquals("rj_001", body.renderJobId());
        assertEquals("prj_1", body.projectId());
        assertEquals("rev_1", body.timelineRevisionId());
        assertEquals("snap_1", body.snapshotId());
        assertEquals("READY", body.status());
        assertEquals("prod_001", body.outputProductId());
        assertEquals("READY", body.productStatus());
        assertNotNull(body.inputProductIds());
        assertEquals(1, body.inputProductIds().size());
        assertEquals("input_1", body.inputProductIds().get(0));
        assertEquals(1, body.inputDependencyCount());
        assertTrue(body.resultAvailable());
    }

    @Test
    @DisplayName("Status endpoint: response does not expose sensitive data")
    void statusEndpointNoSensitiveData() {
        // Arrange
        RenderJobStatusResponse expected = new RenderJobStatusResponse(
                "rj_001", "prj_1", "rev_1", "snap_1",
                "READY", "timeline-revision-render", "default_1080p", "mp4",
                "prod_001", "READY",
                List.of("input_1"), 1,
                "2026-06-27T10:00:00Z", "2026-06-27T10:00:05Z",
                "Render completed successfully", true);
        when(renderJobStatusService.findStatus("prj_1", "rev_1", "rj_001"))
                .thenReturn(Optional.of(expected));

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controller.getRenderJobStatus("prj_1", "rev_1", "rj_001");

        // Assert — verify no sensitive fields exist on the record
        // The record type itself guarantees this structurally:
        // - No storageReferenceId field
        // - No provider/backend/environment fields
        // - No signedUrl/localPath/materializedPath fields
        // - No bucket/key fields
        // - No workerHost/ffmpegCommand fields
        assertNotNull(response.getBody());
        // Verify we can serialize to string without sensitive data leaking
        String serialized = response.getBody().toString();
        assertFalse(serialized.contains("storageReferenceId"));
        assertFalse(serialized.contains("storageProvider"));
        assertFalse(serialized.contains("signedUrl"));
        assertFalse(serialized.contains("localPath"));
        assertFalse(serialized.contains("materializedPath"));
    }

    @Test
    @DisplayName("Status endpoint: unknown renderJobId returns 404")
    void statusEndpointNotFound() {
        // Arrange
        when(renderJobStatusService.findStatus("prj_1", "rev_1", "rj_unknown"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controller.getRenderJobStatus("prj_1", "rev_1", "rj_unknown");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Status endpoint: project mismatch returns 404")
    void statusEndpointProjectMismatch() {
        // Arrange
        when(renderJobStatusService.findStatus("prj_wrong", "rev_1", "rj_001"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controller.getRenderJobStatus("prj_wrong", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Status endpoint: revision mismatch returns 404")
    void statusEndpointRevisionMismatch() {
        // Arrange
        when(renderJobStatusService.findStatus("prj_1", "rev_wrong", "rj_001"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controller.getRenderJobStatus("prj_1", "rev_wrong", "rj_001");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Status endpoint: service unavailable returns 503")
    void statusEndpointServiceUnavailable() {
        // Arrange — controller with null renderJobStatusService
        TimelineRevisionController controllerNoService = new TimelineRevisionController(
                revisionService, mergeService, eventPublisher, renderService, null);

        // Act
        ResponseEntity<RenderJobStatusResponse> response =
                controllerNoService.getRenderJobStatus("prj_1", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNull(response.getBody());
    }

    // ─── Result endpoint tests ───

    @Test
    @DisplayName("Result endpoint: READY render returns 200 OK with safe fields")
    void resultEndpointReadyRender() {
        // Arrange
        RenderJobResultResponse expected = new RenderJobResultResponse(
                "rj_001", "prj_1", "rev_1", "snap_1",
                "prod_001", "READY",
                "video/mp4", "mp4",
                1920, 1080, 30, 10.5, true,
                "ffmpeg-libass", "timeline-revision-render",
                List.of("input_1"), 1,
                "2026-06-27T10:00:00Z", "2026-06-27T10:00:05Z",
                "Render result available");
        when(renderJobStatusService.findResult("prj_1", "rev_1", "rj_001"))
                .thenReturn(Optional.of(expected));

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controller.getRenderJobResult("prj_1", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        RenderJobResultResponse body = response.getBody();
        assertEquals("rj_001", body.renderJobId());
        assertEquals("prj_1", body.projectId());
        assertEquals("rev_1", body.timelineRevisionId());
        assertEquals("snap_1", body.snapshotId());
        assertEquals("prod_001", body.outputProductId());
        assertEquals("READY", body.productStatus());
        assertEquals("video/mp4", body.mimeType());
        assertEquals("mp4", body.outputFormat());
        assertEquals(1920, body.width());
        assertEquals(1080, body.height());
        assertEquals(30, body.fps());
        assertEquals(10.5, body.durationSeconds());
        assertTrue(body.hasSubtitles());
        assertEquals("ffmpeg-libass", body.baselineRenderer());
        assertEquals("timeline-revision-render", body.renderMode());
        assertNotNull(body.inputProductIds());
        assertEquals(1, body.inputProductIds().size());
        assertEquals("input_1", body.inputProductIds().get(0));
        assertEquals(1, body.inputDependencyCount());
    }

    @Test
    @DisplayName("Result endpoint: response does not expose sensitive data")
    void resultEndpointNoSensitiveData() {
        // Arrange
        RenderJobResultResponse expected = new RenderJobResultResponse(
                "rj_001", "prj_1", "rev_1", "snap_1",
                "prod_001", "READY",
                "video/mp4", "mp4",
                1920, 1080, 30, 10.5, true,
                "ffmpeg-libass", "timeline-revision-render",
                List.of("input_1"), 1,
                "2026-06-27T10:00:00Z", "2026-06-27T10:00:05Z",
                "Render result available");
        when(renderJobStatusService.findResult("prj_1", "rev_1", "rj_001"))
                .thenReturn(Optional.of(expected));

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controller.getRenderJobResult("prj_1", "rev_1", "rj_001");

        // Assert — verify no sensitive fields exist on the record
        assertNotNull(response.getBody());
        String serialized = response.getBody().toString();
        assertFalse(serialized.contains("storageReferenceId"));
        assertFalse(serialized.contains("storageProvider"));
        assertFalse(serialized.contains("signedUrl"));
        assertFalse(serialized.contains("localPath"));
        assertFalse(serialized.contains("materializedPath"));
    }

    @Test
    @DisplayName("Result endpoint: unknown renderJobId returns 404")
    void resultEndpointNotFound() {
        // Arrange
        when(renderJobStatusService.findResult("prj_1", "rev_1", "rj_unknown"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controller.getRenderJobResult("prj_1", "rev_1", "rj_unknown");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Result endpoint: project mismatch returns 404")
    void resultEndpointProjectMismatch() {
        // Arrange
        when(renderJobStatusService.findResult("prj_wrong", "rev_1", "rj_001"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controller.getRenderJobResult("prj_wrong", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Result endpoint: revision mismatch returns 404")
    void resultEndpointRevisionMismatch() {
        // Arrange
        when(renderJobStatusService.findResult("prj_1", "rev_wrong", "rj_001"))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controller.getRenderJobResult("prj_1", "rev_wrong", "rj_001");

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Result endpoint: service unavailable returns 503")
    void resultEndpointServiceUnavailable() {
        // Arrange — controller with null renderJobStatusService
        TimelineRevisionController controllerNoService = new TimelineRevisionController(
                revisionService, mergeService, eventPublisher, renderService, null);

        // Act
        ResponseEntity<RenderJobResultResponse> response =
                controllerNoService.getRenderJobResult("prj_1", "rev_1", "rj_001");

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNull(response.getBody());
    }

    // ─── DTO safety verification ───

    @Test
    @DisplayName("RenderJobStatusResponse record has no sensitive fields")
    void statusResponseDtoSafety() {
        // Verify the record type itself does not declare sensitive fields
        // by checking all component names
        String[] componentNames = java.util.Arrays.stream(
                        RenderJobStatusResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toArray(String[]::new);

        List<String> forbiddenFields = List.of(
                "provider", "backend", "environment", "executionEnvironment",
                "storageProvider", "producer", "storageReferenceId",
                "signedUrl", "url", "localPath", "materializedPath",
                "bucket", "key", "workerHost", "ffmpegCommand", "command",
                "processEnvironment", "stackTrace");

        for (String field : forbiddenFields) {
            assertFalse(
                    java.util.Arrays.asList(componentNames).contains(field),
                    "RenderJobStatusResponse must not have field: " + field);
        }
    }

    @Test
    @DisplayName("RenderJobResultResponse record has no sensitive fields")
    void resultResponseDtoSafety() {
        // Verify the record type itself does not declare sensitive fields
        String[] componentNames = java.util.Arrays.stream(
                        RenderJobResultResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toArray(String[]::new);

        List<String> forbiddenFields = List.of(
                "provider", "backend", "environment", "executionEnvironment",
                "storageProvider", "producer", "storageReferenceId",
                "signedUrl", "url", "localPath", "materializedPath",
                "bucket", "key", "workerHost", "ffmpegCommand", "command",
                "processEnvironment", "stackTrace");

        for (String field : forbiddenFields) {
            assertFalse(
                    java.util.Arrays.asList(componentNames).contains(field),
                    "RenderJobResultResponse must not have field: " + field);
        }
    }
}
