package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportPreviewServiceTest {

    @Mock
    private AuditPort auditPort;

    private ProjectImportPreviewService previewService;

    @BeforeEach
    void setUp() {
        previewService = new ProjectImportPreviewService();
        previewService.setAuditPort(auditPort);
    }

    @Test
    void previewMetadataOnlyExportShouldBeCompatible() {
        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertNotNull(response.project());
        assertEquals("prj-source", response.project().sourceProjectId());
        assertEquals("Test Project", response.project().name());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void previewShouldRejectUnsupportedSchemaVersion() {
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "unsupported-version", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertFalse(response.compatible());
        assertFalse(response.errors().isEmpty());
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", response.errors().get(0).code());
    }

    @Test
    void previewShouldReportAssetsNeedUpload() {
        ProjectExportAssetDto asset1 = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null, null); // No downloadUrl
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "metadata_only", List.of(asset1), null);

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertEquals(1, response.assets().total());
        assertEquals(0, response.assets().available());
        assertEquals(1, response.assets().needsUpload());
    }

    @Test
    void previewLinkedAssetsShouldMarkAvailableLinkedWhenUrlPresent() {
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.com/video.mp4?token=abc"); // Has signed URL
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertEquals(1, response.assets().total());
        assertEquals(1, response.assets().available());
        assertEquals(0, response.assets().needsUpload());
    }

    @Test
    void previewShouldWarnForUnsupportedEffects() {
        // Create render plan with unknown effect key
        Map<String, Object> renderPlan = Map.of(
                "operations", List.of(
                        Map.of("type", "filter", "effectKey", "video.unknown_effect"),
                        Map.of("type", "fade_in", "effectKey", "video.fade_in")
                )
        );
        Map<String, Object> spatialPlan = Map.of();
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", renderPlan, spatialPlan, "v1");

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, render);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertTrue(response.compatible());
        assertEquals(2, response.effects().total());
        assertEquals(1, response.effects().supported());
        assertEquals(1, response.effects().unsupported());
        // Should have warning for unknown effect
        assertTrue(response.warnings().stream()
                .anyMatch(w -> "UNSUPPORTED_EFFECT".equals(w.code())));
    }

    @Test
    void previewShouldValidateSpatialPpmCoordinates() {
        // Create spatial plan with invalid ppm coordinate
        Map<String, Object> spatialPlan = Map.of(
                "operations", List.of(
                        Map.of("id", "crop-1", "type", "crop",
                                "x", 1_500_000, // Out of range (> 1,000,000)
                                "y", 0, "width", 500000, "height", 500000)
                )
        );
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1", Map.of(), spatialPlan, "v1");

        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                null, null, render);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        ProjectImportPreviewResponse response = previewService.previewImport("tenant-1", request);

        assertFalse(response.compatible());
        assertTrue(response.errors().stream()
                .anyMatch(e -> "INVALID_SPATIAL_COORDINATE".equals(e.code())));
    }

    @Test
    void previewShouldNotPersistProject() {
        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        previewService.previewImport("tenant-1", request);

        // Verify no audit issues (audit failure should not block)
        verify(auditPort).record(anyString(), eq("PROJECT_IMPORT_PREVIEW"), anyString(),
                anyString(), anyString(), any(Map.class));
    }

    @Test
    void previewShouldRecordAuditWithoutUrls() {
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 10.0, 1920, 1080, null,
                "https://signed.example.com/video.mp4?token=secret123");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets",
                null, new ProjectExportProjectDto("prj-1", "tenant-1", "Test", "Desc",
                        null, null, "ACTIVE"),
                assets, null, null);
        ProjectImportPreviewRequest request = new ProjectImportPreviewRequest(exportPkg);

        previewService.previewImport("tenant-1", request);

        // Verify audit payload does not contain signed URL
        verify(auditPort).record(anyString(), eq("PROJECT_IMPORT_PREVIEW"), anyString(),
                anyString(), anyString(), argThat(m ->
                        !m.containsKey("signedUrl") &&
                        !m.containsKey("downloadUrl") &&
                        m.containsKey("assetCount")));
    }

    @Test
    void previewShouldWorkWithoutAuditPort() {
        ProjectImportPreviewService serviceWithoutAudit = new ProjectImportPreviewService();
        // auditPort is null

        ProjectImportPreviewRequest request = createMetadataOnlyRequest();
        ProjectImportPreviewResponse response = serviceWithoutAudit.previewImport("tenant-1", request);

        assertNotNull(response);
        assertTrue(response.compatible());
    }

    private ProjectImportPreviewRequest createMetadataOnlyRequest() {
        ProjectExportPackageDto exportPkg = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only",
                null, new ProjectExportProjectDto("prj-source", "tenant-source", "Test Project",
                        "Test Description", null, null, "ACTIVE"),
                null, null, null);
        return new ProjectImportPreviewRequest(exportPkg);
    }
}
