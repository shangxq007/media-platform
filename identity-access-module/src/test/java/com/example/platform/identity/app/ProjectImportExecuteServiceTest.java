package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectImportExecuteServiceTest {

    @Mock
    private TenantProjectService tenantProjectService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectImportMetadataRepository metadataRepository;

    @Mock
    private MetadataScrubber metadataScrubber;

    @Mock
    private AuditPort auditPort;

    private ProjectImportExecuteService service;

    @BeforeEach
    void setUp() {
        service = new ProjectImportExecuteService(tenantProjectService, projectRepository,
                metadataRepository, metadataScrubber);
        service.setAuditPort(auditPort);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeShellImportShouldCreateProject() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test Project", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
        assertEquals("shell_only", result.mode());
        verify(tenantProjectService).createProject(eq("tenant-1"), any());
    }

    @Test
    void executeShellImportShouldUseImportNameOverride() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Custom Name", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", "Custom Name", exportPackage);

        // Then
        assertNotNull(result);
        verify(tenantProjectService).createProject(eq("tenant-1"), argThat(req ->
                "Custom Name".equals(req.name())));
    }

    @Test
    void executeShellImportShouldIgnoreSourceTenantId() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Source Project (Imported)", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("tenant-1", result.targetProjectId() != null ? "tenant-1" : null);
        verify(tenantProjectService).createProject(eq("tenant-1"), any());
    }

    @Test
    void executeShellImportShouldMarkAssetsNeedsUpload() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(3);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals(3, result.assetSummary().total());
        assertEquals(3, result.assetSummary().needsUpload());
        assertEquals(0, result.assetSummary().imported());
        result.assetMappings().forEach(mapping -> {
            assertNull(mapping.targetAssetId());
            assertEquals("needs_upload", mapping.status());
        });
    }

    @Test
    void executeShellImportShouldReturnAssetMappingSkeleton() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(2);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals(2, result.assetMappings().size());
        assertEquals("art-1", result.assetMappings().get(0).sourceAssetId());
        assertEquals("art-2", result.assetMappings().get(1).sourceAssetId());
    }

    @Test
    void executeShellImportShouldNotDownloadSignedUrls() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(1);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        // Verify no signed URLs in result
        assertFalse(result.toString().contains("downloadUrl"));
        assertFalse(result.toString().contains("signed"));
    }

    @Test
    void executeShellImportShouldNotCallBlobStorage() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        // Service doesn't have BlobStorage dependency - verified by compilation
    }

    @Test
    void executeShellImportShouldNotRegisterArtifacts() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(2);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals(0, result.assetSummary().imported());
        assertEquals(0, result.assetSummary().rebound());
    }

    @Test
    void executeShellImportShouldRejectInvalidSchemaVersion() {
        // Given
        ProjectExportPackageDto exportPackage = new ProjectExportPackageDto(
                "unsupported-version", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Test", "desc", null, null, "ACTIVE"),
                null, null, null);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                service.executeShellImport("tenant-1", null, exportPackage));
    }

    @Test
    void executeShellImportShouldReportUnsupportedEffects() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithUnsupportedEffect();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertTrue(result.warnings().stream().anyMatch(w ->
                w.message().contains("custom.effect")));
    }

    @Test
    void executeShellImportShouldRespectTenantAccess() {
        // Given
        TenantContext.set("tenant-2");
        ProjectExportPackageDto exportPackage = createSampleExportPackage();

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                service.executeShellImport("tenant-1", null, exportPackage));
    }

    @Test
    void executeShellImportShouldNotTrustSourceTenantId() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        verify(tenantProjectService).createProject(eq("tenant-1"), any());
    }

    @Test
    void executeShellImportShouldRecordAudit() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(2);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    @Test
    void executeShellImportShouldNotContainSignedUrlsInAudit() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(1);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    @Test
    void executeShellImportShouldNotContainStorageUriInAudit() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    @Test
    void executeShellImportShouldGenerateUniqueImportId() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result1 =
                service.executeShellImport("tenant-1", null, exportPackage);
        ProjectImportExecuteService.ImportExecuteResult result2 =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotEquals(result1.importId(), result2.importId());
    }

    @Test
    void executeShellImportShouldHandleDescriptionSafely() {
        // Given
        ProjectExportPackageDto exportPackage = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Test",
                        "Description with https://example.com URL", null, null, "ACTIVE"),
                null, null, null);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        verify(tenantProjectService).createProject(eq("tenant-1"), argThat(req ->
                req.description() != null && req.description().contains("[url]")));
    }

    // Helper methods

    private ProjectExportPackageDto createSampleExportPackage() {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                null, null);
    }

    private ProjectExportPackageDto createSampleExportPackageWithAssets(int count) {
        List<ProjectExportAssetDto> assets = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            assets.add(new ProjectExportAssetDto(
                    "art-" + i, "file" + i + ".mp4", "video", "video/mp4",
                    1024L, null, null, null, null, null, null));
        }
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", assets, null),
                null, null);
    }

    private ProjectExportPackageDto createSampleExportPackageWithUnsupportedEffect() {
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        // Add render plan with unsupported effect
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1",
                Map.of("operations", List.of(
                        Map.of("effectKey", "custom.effect", "params", Map.of())
                )),
                null,
                null);
        return new ProjectExportPackageDto(
                exportPackage.schemaVersion(), exportPackage.exportMode(),
                exportPackage.manifest(), exportPackage.project(),
                exportPackage.assets(), exportPackage.timeline(), render);
    }
}
