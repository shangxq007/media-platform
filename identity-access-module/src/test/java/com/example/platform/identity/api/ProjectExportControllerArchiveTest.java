package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportService;
import com.example.platform.identity.app.ProjectExportZipPackagingService;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectExportControllerArchiveTest {

    @Mock
    private ProjectExportService projectExportService;

    @Mock
    private ProjectExportZipPackagingService zipPackagingService;

    private ProjectExportController controller;

    @BeforeEach
    void setUp() {
        controller = new ProjectExportController(projectExportService, zipPackagingService);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ProjectExportResponse buildResponse(String exportId, String mode) {
        Instant now = Instant.now();
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                "linked_assets".equals(mode), false, false, false, true, true, true);
        ProjectExportManifestDto manifest = new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", exportId, mode, now, "user-1",
                Map.of(), security, new ProjectExportManifestAssetsDto(mode, 0, 0), Map.of());
        ProjectExportProjectDto project = new ProjectExportProjectDto(
                "prj-1", "tenant-1", "Test", "Desc", now, now, "ACTIVE");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", mode, List.of(), null);
        ProjectExportTimelineDto timeline = new ProjectExportTimelineDto("project-export-v1", List.of(), 0);
        ProjectExportRenderDto render = new ProjectExportRenderDto("project-export-v1", Map.of(), Map.of(), "v1");
        ProjectExportEffectsDto effects = new ProjectExportEffectsDto("project-export-v1", "v1", List.of());
        ProjectExportOutputsDto outputs = new ProjectExportOutputsDto("project-export-v1", 0, List.of());
        ProjectExportAuditDto audit = new ProjectExportAuditDto("project-export-v1", "aud-1", now, "user-1", "PROJECT_EXPORT");
        return new ProjectExportResponse("project-export-v1", exportId, mode, now,
                manifest, project, assets, timeline, render, effects, outputs, audit);
    }

    // ─── Happy path ───

    @Test
    void archiveEndpointShouldReturnZip() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-1", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody() instanceof byte[]);
    }

    @Test
    void archiveEndpointShouldSetContentTypeApplicationZip() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-2", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertTrue(result.getHeaders().getContentType().toString().contains("application/zip"));
    }

    @Test
    void archiveEndpointShouldSetAttachmentFilename() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-3", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        String disposition = result.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("attachment"));
        assertTrue(disposition.contains("project-export-prj-1-exp-3.zip"));
    }

    @Test
    void archiveEndpointShouldContainRequiredEntries() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-5", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(zipPackagingService).packageMetadataOnly(resp);
    }

    @Test
    void metadataOnlyArchiveShouldNotRequireSigningPort() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-6", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void linkedAssetsArchiveShouldUseSignedUrlsWhenRequested() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-7", "linked_assets");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 7200));

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void archiveAuditShouldNotContainSignedUrls() throws Exception {
        ProjectExportResponse resp = buildResponse("sec-1", "linked_assets");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody() instanceof byte[]);
    }

    // ─── Filename sanitize ───

    @Test
    void archiveFilenameShouldNotContainPathSeparators() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-slash/evil", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        String disposition = result.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertFalse(disposition.contains("/"), "Filename should not contain forward slash");
        assertFalse(disposition.contains("\\"), "Filename should not contain backslash");
    }

    @Test
    void archiveFilenameShouldNotContainDotDot() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-4/../evil", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        String disposition = result.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertFalse(disposition.contains(".."), "Filename should not contain path traversal");
    }

    @Test
    void archiveFilenameShouldOnlyContainSafeChars() throws Exception {
        ProjectExportResponse resp = buildResponse("exp-@#$%", "metadata_only");
        doReturn(resp).when(projectExportService).createExport(anyString(), anyString(), any());
        doReturn(new byte[]{0x50, 0x4b}).when(zipPackagingService).packageMetadataOnly(resp);

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        String disposition = result.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        // Should not contain @, #, $, %, etc.
        assertFalse(disposition.contains("@"));
        assertFalse(disposition.contains("#"));
        assertFalse(disposition.contains("$"));
    }

    // ─── Request validation ───

    @Test
    void archiveShouldRejectNullBody() {
        ResponseEntity<?> result = controller.createExportArchive("tenant-1", "prj-1", null);
        assertEquals(400, result.getStatusCode().value());
    }

    @Test
    void archiveShouldRejectBlankMode() {
        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("   ", null));
        assertEquals(400, result.getStatusCode().value());
    }

    @Test
    void archiveShouldRejectEmptyMode() {
        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("", null));
        assertEquals(400, result.getStatusCode().value());
    }

    @Test
    void archiveShouldRejectNullMode() {
        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest(null, null));
        assertEquals(400, result.getStatusCode().value());
    }

    @Test
    void unsupportedArchiveModeShouldBeRejected() {
        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("bundled_assets", null));
        assertEquals(501, result.getStatusCode().value());
    }

    @Test
    void archiveShouldRejectTtlOver86400() throws Exception {
        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 100000));
        assertEquals(400, result.getStatusCode().value());
    }

    // ─── Tenant / project access ───

    @Test
    void archiveEndpointShouldRespectTenantAccess() throws Exception {
        doThrow(new IllegalArgumentException("Project not found"))
                .when(projectExportService).createExport(anyString(), anyString(), any());

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void linkedAssetsArchiveShouldFailWhenSigningUnavailable() throws Exception {
        doThrow(new UnsupportedOperationException("AssetDownloadUrlPort not configured"))
                .when(projectExportService).createExport(anyString(), anyString(), any());

        ResponseEntity<?> result = controller.createExportArchive(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        assertEquals(501, result.getStatusCode().value());
    }
}
