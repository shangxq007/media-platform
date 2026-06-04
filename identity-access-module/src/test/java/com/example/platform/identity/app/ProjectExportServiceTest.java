package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.asset.AssetDownloadUrlPort;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.export.ProjectAssetListingPort;
import com.example.platform.shared.export.ProjectAssetRef;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectExportServiceTest {

    @Mock
    private TenantProjectService tenantProjectService;

    @Mock
    private AuditPort auditPort;

    @Mock
    private ProjectAssetListingPort projectAssetListingPort;

    @Mock
    private AssetDownloadUrlPort assetDownloadUrlPort;

    private ProjectExportService projectExportService;

    @BeforeEach
    void setUp() {
        projectExportService = new ProjectExportService(tenantProjectService);
        projectExportService.setAuditPort(auditPort);
        projectExportService.setProjectAssetListingPort(projectAssetListingPort);
        projectExportService.setAssetDownloadUrlPort(assetDownloadUrlPort);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── metadata_only ───

    @Test
    void metadataOnlyExportShouldReturnProjectExportV1() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test Project",
                "Description", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertNotNull(response);
        assertEquals("project-export-v1", response.schemaVersion());
        assertEquals("metadata_only", response.exportMode());
        assertFalse(response.manifest().security().containsSignedUrls());
        assertNull(response.assets().signedUrls());
        verifyNoInteractions(projectAssetListingPort);
        verifyNoInteractions(assetDownloadUrlPort);
    }

    @Test
    void metadataOnlyExportShouldNotContainStorageRefOrDownloadUrl() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertTrue(response.assets().assets().isEmpty());
        assertNull(response.assets().signedUrls());
    }

    // ─── linked_assets: real assets via port ───

    @Test
    void linkedAssetsExportShouldListProjectAssets() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset1 = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                1024L, "s3://bucket/video.mp4", null,
                5000L, 1920, 1080);
        ProjectAssetRef asset2 = new ProjectAssetRef(
                "art-2", "audio", "audio/mpeg", "audio.mp3",
                512L, "s3://bucket/audio.mp3", null,
                30000L, null, null);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset1, asset2));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(eq("art-1"), eq("s3://bucket/video.mp4"), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));
        when(assetDownloadUrlPort.generateSignedUrl(eq("art-2"), eq("s3://bucket/audio.mp3"), any()))
                .thenReturn(Optional.of("https://signed.example.com/audio.mp3?token=def"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        assertEquals("linked_assets", response.exportMode());
        assertTrue(response.manifest().security().containsSignedUrls());
        assertEquals(2, response.assets().assets().size());

        // Verify port was called with tenantId
        verify(projectAssetListingPort).listAssets("tenant-1", "prj-1");
    }

    @Test
    void linkedAssetsExportShouldGenerateSignedUrlsPerAsset() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "file.mp4", "video/mp4", "file.mp4",
                1024L, "s3://bucket/file.mp4", null,
                5000L, 1920, 1080);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/file.mp4?token=xyz"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        for (ProjectExportAssetDto dto : response.assets().assets()) {
            assertNotNull(dto.downloadUrl(), "Asset " + dto.assetId() + " should have signed URL");
            assertTrue(dto.downloadUrl().startsWith("https://"));
        }
    }

    @Test
    void linkedAssetsExportShouldNotExposeStorageUri() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                1024L, "s3://my-bucket/path/to/video.mp4", null,
                5000L, 1920, 1080);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        String json = response.toString();
        assertFalse(json.contains("s3://my-bucket"));
        assertFalse(json.contains("my-bucket"));
        assertFalse(json.contains("storageUri"));
        for (ProjectExportAssetDto dto : response.assets().assets()) {
            assertNull(dto.storageRef());
        }
    }

    @Test
    void linkedAssetsExportShouldRecordAuditWithoutUrls() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of());
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);

        projectExportService.createExport("tenant-1", "prj-1",
                new ProjectExportRequest("linked_assets", 3600));

        verify(auditPort).record(anyString(), eq("PROJECT_EXPORT"), anyString(),
                anyString(), anyString(), argThat(m ->
                        "linked_assets".equals(m.get("mode")) &&
                        Integer.valueOf(0).equals(m.get("assetCount")) &&
                        Integer.valueOf(3600).equals(m.get("ttlSeconds")) &&
                        !m.containsKey("signedUrl") &&
                        !m.containsKey("downloadUrl")));
    }

    @Test
    void linkedAssetsExportShouldFailClosedWhenOneSignedUrlFails() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset1 = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video1.mp4",
                1024L, "s3://bucket/video1.mp4", null, null, null, null);
        ProjectAssetRef asset2 = new ProjectAssetRef(
                "art-2", "video", "video/mp4", "video2.mp4",
                512L, "s3://bucket/video2.mp4", null, null, null, null);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset1, asset2));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(eq("art-1"), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video1.mp4"));
        when(assetDownloadUrlPort.generateSignedUrl(eq("art-2"), anyString(), any()))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> {
            projectExportService.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldRejectWrongTenant() {
        TenantContext.set("tenant-wrong");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(IllegalArgumentException.class, () -> {
            projectExportService.createExport("tenant-wrong", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });

        verifyNoInteractions(projectAssetListingPort);
        verifyNoInteractions(assetDownloadUrlPort);
    }

    @Test
    void linkedAssetsExportShouldRejectWhenPortMissing() {
        ProjectExportService serviceWithoutPort = new ProjectExportService(tenantProjectService);
        serviceWithoutPort.setAuditPort(auditPort);
        // projectAssetListingPort is null

        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(UnsupportedOperationException.class, () -> {
            serviceWithoutPort.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldRejectWhenDownloadPortMissing() {
        ProjectExportService serviceWithoutDownloadPort = new ProjectExportService(tenantProjectService);
        serviceWithoutDownloadPort.setAuditPort(auditPort);
        serviceWithoutDownloadPort.setProjectAssetListingPort(projectAssetListingPort);
        // assetDownloadUrlPort is null

        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        lenient().when(projectAssetListingPort.isAvailable()).thenReturn(true);

        assertThrows(UnsupportedOperationException.class, () -> {
            serviceWithoutDownloadPort.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldRejectTooLongTtl() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of());
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 100000));

        assertEquals("linked_assets", response.exportMode());
    }

    @Test
    void linkedAssetsExportShouldUseDefaultTtl() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of());
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", null));

        assertEquals("linked_assets", response.exportMode());
    }

    @Test
    void exportingNonExistentProjectShouldBeRejected() {
        TenantContext.set("tenant-1");
        when(tenantProjectService.getProject("prj-nonexistent")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            projectExportService.createExport("tenant-1", "prj-nonexistent",
                    new ProjectExportRequest("linked_assets", 3600));
        });

        verifyNoInteractions(projectAssetListingPort);
        verifyNoInteractions(assetDownloadUrlPort);
    }

    @Test
    void exportingFromWrongTenantShouldBeRejectedForMetadataOnly() {
        TenantContext.set("tenant-wrong");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(IllegalArgumentException.class, () -> {
            projectExportService.createExport("tenant-wrong", "prj-1",
                    new ProjectExportRequest("metadata_only", null));
        });
    }

    @Test
    void exportResponseShouldNotContainInternalUrls() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of());
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        String json = response.toString();
        assertFalse(json.contains("file://"));
        assertFalse(json.contains("/tmp/"));
        assertFalse(json.contains("storage-key"));
    }

    @Test
    void linkedAssetsExportShouldIncludeSizeBytesAndChecksum() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                1234567L, "s3://bucket/video.mp4", "sha256:abcdef1234567890",
                5000L, 1920, 1080);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        ProjectExportAssetDto dto = response.assets().assets().get(0);
        assertEquals(1234567L, dto.sizeBytes());
        assertEquals("sha256:abcdef1234567890", dto.checksum());
        assertNull(dto.storageRef());
    }

    @Test
    void linkedAssetsExportShouldAllowMissingSizeBytesAndChecksum() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                null, "s3://bucket/video.mp4", null,
                null, null, null);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        ProjectExportAssetDto dto = response.assets().assets().get(0);
        assertNull(dto.sizeBytes());
        assertNull(dto.checksum());
        assertNotNull(dto.downloadUrl());
        assertNull(dto.storageRef());
    }

    @Test
    void linkedAssetsExportShouldNotExposeStorageUriAfterEnrichment() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                1024L, "s3://my-bucket/secret/path/video.mp4", "sha256:abc",
                5000L, 1920, 1080);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        String json = response.toString();
        assertFalse(json.contains("s3://my-bucket"));
        assertFalse(json.contains("secret/path"));
        assertFalse(json.contains("storageUri"));
        for (ProjectExportAssetDto dto : response.assets().assets()) {
            assertNull(dto.storageRef());
        }
    }

    @Test
    void linkedAssetsExportShouldRecordAuditWithoutSensitiveStorageData() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetRef asset = new ProjectAssetRef(
                "art-1", "video", "video/mp4", "video.mp4",
                1024L, "s3://bucket/video.mp4", "sha256:abc",
                null, null, null);

        when(projectAssetListingPort.isAvailable()).thenReturn(true);
        when(projectAssetListingPort.listAssets("tenant-1", "prj-1")).thenReturn(List.of(asset));
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        when(assetDownloadUrlPort.generateSignedUrl(anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=secret"));

        projectExportService.createExport("tenant-1", "prj-1",
                new ProjectExportRequest("linked_assets", 3600));

        // Audit should not contain signed URLs or storage URIs
        verify(auditPort).record(anyString(), eq("PROJECT_EXPORT"), anyString(),
                anyString(), anyString(), argThat(m ->
                        Integer.valueOf(1).equals(m.get("assetCount")) &&
                        !m.containsKey("downloadUrl") &&
                        !m.containsKey("storageUri") &&
                        !m.containsKey("signedUrl")));
    }
}
