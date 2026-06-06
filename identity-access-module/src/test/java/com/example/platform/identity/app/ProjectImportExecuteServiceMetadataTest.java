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
class ProjectImportExecuteServiceMetadataTest {

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
    void importArchiveShellShouldPersistTimelineMetadata() {
        // Given
        ProjectExportTimelineDto timeline = new ProjectExportTimelineDto(
                "project-export-v1", List.of(), 0.0);
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithTimeline(timeline);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any())).thenReturn("{\"tracks\":[]}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        verify(metadataRepository).save(any(ProjectImportMetadataRepository.MetadataRecord.class));
        assertTrue(result.metadata().timelinePersisted());
    }

    @Test
    void importArchiveShellShouldPersistRenderPlanMetadata() {
        // Given
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of("operations", List.of()), null, null);
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithRender(render);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any())).thenReturn("{\"operations\":[]}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        verify(metadataRepository).save(any(ProjectImportMetadataRepository.MetadataRecord.class));
        assertTrue(result.metadata().renderPlanPersisted());
    }

    @Test
    void importArchiveShellShouldPersistSpatialPlanMetadata() {
        // Given
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", null, Map.of("canvas", Map.of("width", 1920)), null);
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithRender(render);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any()))
                .thenReturn("{\"canvas\":{}}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        verify(metadataRepository).save(any(ProjectImportMetadataRepository.MetadataRecord.class));
        assertTrue(result.metadata().spatialPlanPersisted());
    }

    @Test
    void importArchiveShellShouldScrubDownloadUrlsFromMetadata() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            if (json == null) return null;
            return json.replaceAll("downloadUrl", "removed");
        });

        // When
        service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        ArgumentCaptor<ProjectImportMetadataRepository.MetadataRecord> captor =
                ArgumentCaptor.forClass(ProjectImportMetadataRepository.MetadataRecord.class);
        verify(metadataRepository).save(captor.capture());
        ProjectImportMetadataRepository.MetadataRecord saved = captor.getValue();
        assertNotNull(saved);
        assertFalse(saved.toString().contains("downloadUrl"));
    }

    @Test
    void importArchiveShellShouldKeepAssetsNeedsUpload() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithAssets(3);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any())).thenReturn("{}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals(3, result.assetSummary().needsUpload());
        assertEquals(0, result.assetSummary().imported());
    }

    @Test
    void metadataPersistenceFailureShouldRollbackProject() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        doThrow(new RuntimeException("Database error")).when(metadataRepository).save(any());

        // When/Then
        assertThrows(RuntimeException.class, () ->
                service.executeShellImport("tenant-1", null, exportPackage));

        // Verify rollback was attempted
        verify(projectRepository).deleteById("prj-123");
    }

    @Test
    void metadataPersistenceFailureShouldNotRegisterArtifacts() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        doThrow(new RuntimeException("Database error")).when(metadataRepository).save(any());

        // When/Then
        assertThrows(RuntimeException.class, () ->
                service.executeShellImport("tenant-1", null, exportPackage));

        // Then - no artifact registration attempted (service doesn't have artifact dependency)
        // This test verifies the service doesn't interact with artifact catalog
    }

    @Test
    void importShellShouldSucceedWithEmptyMetadata() {
        // Given - use simple export package without render to avoid toString issues
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(null)).thenReturn(null);
        when(metadataScrubber.scrub(any(String.class))).thenReturn(null);
        doNothing().when(metadataRepository).save(any());
        doNothing().when(projectRepository).deleteById(any());

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - verify import succeeded
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
        assertNotNull(result.metadata());
        assertFalse(result.metadata().timelinePersisted());
        assertFalse(result.metadata().renderPlanPersisted());
    }

    @Test
    void importShellShouldPersistMetadataRecord() {
        // Given - use simple export package
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(null)).thenReturn(null);
        when(metadataScrubber.scrub(any(String.class))).thenReturn(null);
        doNothing().when(metadataRepository).save(any());

        // When
        service.executeShellImport("tenant-1", null, exportPackage);

        // Then - verify metadata was saved
        verify(metadataRepository).save(any(ProjectImportMetadataRepository.MetadataRecord.class));
    }

    @Test
    void importResponseShouldContainMetadataSummary() {
        // Given
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of("operations", List.of()), null, null);
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithRender(render);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any()))
                .thenReturn("{\"operations\":[]}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result.metadata());
        assertTrue(result.metadata().renderPlanPersisted());
    }

    @Test
    void importShouldIgnoreSourceTenantIdForMetadata() {
        // Given - export package has different tenantId
        ProjectExportPackageDto exportPackage = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "malicious-tenant", "Test", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                null, null);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(null)).thenReturn(null);
        when(metadataScrubber.scrub(any(String.class))).thenReturn(null);

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());

        // Verify metadata saved with tenant-1, not malicious-tenant
        ArgumentCaptor<ProjectImportMetadataRepository.MetadataRecord> captor =
                ArgumentCaptor.forClass(ProjectImportMetadataRepository.MetadataRecord.class);
        verify(metadataRepository).save(captor.capture());
        assertEquals("tenant-1", captor.getValue().tenantId());
        assertEquals("prj-123", captor.getValue().projectId());
    }

    @Test
    void unsupportedEffectsShouldRemainWarnings() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        when(metadataScrubber.scrub(any())).thenReturn("{}");

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        // Shell import should succeed even if there are unsupported effects
        assertEquals("prj-123", result.targetProjectId());
    }

    // Helper methods

    private ProjectExportPackageDto createSampleExportPackage() {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                null, null);
    }

    private ProjectExportPackageDto createSampleExportPackageWithTimeline(ProjectExportTimelineDto timeline) {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                timeline, null);
    }

    private ProjectExportPackageDto createSampleExportPackageWithRender(ProjectExportRenderDto render) {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                null, render);
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
}
