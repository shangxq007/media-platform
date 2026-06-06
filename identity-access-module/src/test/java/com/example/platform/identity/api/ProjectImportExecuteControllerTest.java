package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportZipReader;
import com.example.platform.identity.app.ProjectImportExecuteService;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportExecuteControllerTest {

    private static final ImportMetadataSummaryDto DEFAULT_METADATA =
            new ImportMetadataSummaryDto(false, false, false, false);

    @Mock
    private ProjectImportExecuteService importExecuteService;

    @Mock
    private ProjectExportZipReader zipReader;

    @Mock
    private AuditPort auditPort;

    private ProjectImportExecuteController controller;

    @BeforeEach
    void setUp() {
        controller = new ProjectImportExecuteController(importExecuteService, zipReader);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void importArchiveShellShouldCreateProject() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(2, 0, 2, 0, 0),
                        List.of(
                                new ProjectImportAssetMappingDto("art-1", null, "needs_upload"),
                                new ProjectImportAssetMappingDto("art-2", null, "needs_upload")
                        ),
                        List.of(),
                        DEFAULT_METADATA
                );

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(eq("tenant-1"), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(200, response.getStatusCode().value());
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertEquals("imp-123", body.importId());
        assertEquals("SUCCEEDED", body.status());
        assertEquals("prj-456", body.targetProjectId());
        assertEquals("shell_only", body.mode());
        assertEquals(2, body.assets().total());
        assertEquals(2, body.assetMappings().size());
        assertNotNull(body.metadata());
    }

    @Test
    void importArchiveShellShouldUseImportNameOverride() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(eq("tenant-1"), eq("My Custom Name"), any()))
                .thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, "My Custom Name", "shell_only");

        // Then
        assertEquals(200, response.getStatusCode().value());
        verify(importExecuteService).executeShellImport("tenant-1", "My Custom Name", readResult.exportPackage());
    }

    @Test
    void importArchiveShellShouldIgnoreSourceTenantId() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(eq("tenant-1"), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(200, response.getStatusCode().value());
        // Verify that executeShellImport is called with path tenantId, not source tenantId
        verify(importExecuteService).executeShellImport(eq("tenant-1"), any(), any());
    }

    @Test
    void importArchiveShellShouldMarkAssetsNeedsUpload() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(3, 0, 3, 0, 0),
                        List.of(
                                new ProjectImportAssetMappingDto("art-1", null, "needs_upload"),
                                new ProjectImportAssetMappingDto("art-2", null, "needs_upload"),
                                new ProjectImportAssetMappingDto("art-3", null, "needs_upload")
                        ),
                        List.of(),
                        DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertEquals(3, body.assets().needsUpload());
        assertEquals(0, body.assets().imported());
        body.assetMappings().forEach(mapping -> {
            assertNull(mapping.targetAssetId());
            assertEquals("needs_upload", mapping.status());
        });
        assertNotNull(body.metadata());
    }

    @Test
    void importArchiveShellShouldReturnAssetMappingSkeleton() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(2, 0, 2, 0, 0),
                        List.of(
                                new ProjectImportAssetMappingDto("art-1", null, "needs_upload"),
                                new ProjectImportAssetMappingDto("art-2", null, "needs_upload")
                        ),
                        List.of(),
                        DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertEquals(2, body.assetMappings().size());
        assertEquals("art-1", body.assetMappings().get(0).sourceAssetId());
        assertEquals("art-2", body.assetMappings().get(1).sourceAssetId());
    }

    @Test
    void importArchiveShellShouldNotDownloadSignedUrls() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(1, 0, 1, 0, 0),
                        List.of(new ProjectImportAssetMappingDto("art-1", null, "needs_upload")),
                        List.of(),
                        DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        // Verify no signed URLs in response
        assertFalse(body.toString().contains("downloadUrl"));
        assertFalse(body.toString().contains("signed"));
    }

    @Test
    void importArchiveShellShouldNotCallBlobStorage() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then - verify response doesn't contain storage references
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertEquals(0, body.assets().imported());
        assertNotNull(body.assetMappings());
    }

    @Test
    void importArchiveShellShouldNotRegisterArtifacts() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertEquals(0, body.assets().imported());
        assertEquals(0, body.assets().rebound());
    }

    @Test
    void importArchiveShellShouldRejectInvalidZip() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                null, List.of(), List.of("Invalid zip structure"), false);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("invalid_archive", body.get("error"));
    }

    @Test
    void importArchiveShellShouldRejectChecksumMismatch() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                null, List.of(), List.of("Checksum mismatch for assets.json"), false);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("invalid_archive", body.get("error"));
    }

    @Test
    void importArchiveShellShouldRejectZipSlip() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                null, List.of(), List.of("Zip entry name cannot contain '..': ../../../etc/passwd"), false);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("invalid_archive", body.get("error"));
    }

    @Test
    void importArchiveShellShouldRollbackProjectOnMetadataFailure() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Schema version mismatch"));

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("validation_failed", body.get("error"));
    }

    @Test
    void importArchiveShellAuditShouldNotContainSignedUrls() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then - verify response doesn't contain sensitive data
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertFalse(body.toString().contains("downloadUrl"));
        assertFalse(body.toString().contains("signed"));
    }

    @Test
    void importArchiveShellAuditShouldNotContainStorageUri() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertFalse(body.toString().contains("storageUri"));
        assertFalse(body.toString().contains("storageRef"));
        assertFalse(body.toString().contains("bucket"));
    }

    @Test
    void importArchiveShellShouldReportUnsupportedEffects() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(),
                        List.of(new ImportPreviewIssueDto("UNSUPPORTED_EFFECT", "warning",
                                "Effect 'custom.effect' is not in the known taxonomy", null)),
                        DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(any(), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        ProjectImportExecuteResponse body = (ProjectImportExecuteResponse) response.getBody();
        assertNotNull(body);
        assertTrue(body.warnings().stream().anyMatch(w -> w.contains("custom.effect")));
    }

    @Test
    void importArchiveShellShouldRespectTenantAccess() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        TenantContext.set("tenant-2"); // Different tenant

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(eq("tenant-1"), any(), any()))
                .thenThrow(new IllegalArgumentException("Resource not found for tenant: tenant-1"));

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void importArchiveShellShouldNotTrustSourceTenantId() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();
        ProjectImportExecuteService.ImportExecuteResult result =
                new ProjectImportExecuteService.ImportExecuteResult(
                        "imp-123", "prj-456", "shell_only",
                        new ProjectImportAssetSummaryDto(0, 0, 0, 0, 0),
                        List.of(), List.of(), DEFAULT_METADATA);

        ProjectExportZipReader.ZipReadResult readResult = new ProjectExportZipReader.ZipReadResult(
                createSampleExportPackage(), List.of(), List.of(), true);

        when(zipReader.readArchive(any(), anyLong())).thenReturn(readResult);
        when(importExecuteService.executeShellImport(eq("tenant-1"), any(), any())).thenReturn(result);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(200, response.getStatusCode().value());
        // Verify that the service is called with path tenantId, not source
        verify(importExecuteService).executeShellImport(eq("tenant-1"), any(), any());
    }

    @Test
    void importArchiveShellShouldRejectMissingFile() {
        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", null, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("missing_file", body.get("error"));
    }

    @Test
    void importArchiveShellShouldRejectEmptyFile() {
        // Given
        MultipartFile file = new MockMultipartFile("file", new byte[0]);

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "shell_only");

        // Then
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("missing_file", body.get("error"));
    }

    @Test
    void importArchiveShellShouldRejectUnsupportedMode() throws IOException {
        // Given
        MultipartFile file = createValidZipFile();

        // When
        ResponseEntity<?> response = controller.importFromArchive("tenant-1", file, null, "full_import");

        // Then
        assertEquals(501, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("unsupported_mode", body.get("error"));
    }

    // Helper methods

    private MultipartFile createValidZipFile() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        // Add manifest.json
        addToZip(zos, "project-export-v1/manifest.json",
                "{\"schemaVersion\":\"project-export-v1\",\"exportMode\":\"metadata_only\"}");

        // Add project.json
        addToZip(zos, "project-export-v1/project.json",
                "{\"projectId\":\"src-prj-1\",\"tenantId\":\"src-tenant\",\"name\":\"Source Project\",\"description\":\"desc\",\"status\":\"ACTIVE\"}");

        // Add assets.json
        addToZip(zos, "project-export-v1/assets.json",
                "{\"schemaVersion\":\"project-export-v1\",\"exportMode\":\"metadata_only\",\"assets\":[{\"assetId\":\"art-1\",\"filename\":\"video.mp4\",\"type\":\"video\",\"mimeType\":\"video/mp4\",\"sizeBytes\":1024}]}");

        zos.close();
        byte[] zipBytes = baos.toByteArray();

        return new MockMultipartFile("file", "project-export-v1.zip", "application/zip", zipBytes);
    }

    private void addToZip(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private ProjectExportPackageDto createSampleExportPackage() {
        return new ProjectExportPackageDto(
                "project-export-v1",
                "metadata_only",
                null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only",
                        List.of(new ProjectExportAssetDto("art-1", "video.mp4", "video", "video/mp4", 1024L, null, null, null, null, null, null)),
                        null),
                null,
                null
        );
    }
}
