package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.export.ProjectAssetDescriptor;
import com.example.platform.shared.export.ProjectAssetExportPort;
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
    private ProjectAssetExportPort projectAssetExportPort;

    private ProjectExportService projectExportService;

    @BeforeEach
    void setUp() {
        projectExportService = new ProjectExportService(tenantProjectService);
        projectExportService.setAuditPort(auditPort);
        projectExportService.setProjectAssetExportPort(projectAssetExportPort);
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
        assertEquals("metadata_only", response.exportMode());
        assertFalse(response.manifest().security().containsSignedUrls());
        assertNull(response.assets().signedUrls());
        verifyNoInteractions(projectAssetExportPort);
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
    void linkedAssetsExportShouldIncludeRealProjectAssets() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        // Mock assets from port
        ProjectAssetDescriptor asset1 = new ProjectAssetDescriptor(
                "art-1", "video.mp4", "video", "video/mp4",
                1024, "sha256-abc", 10.0, 1920, 1080,
                "s3://bucket/video.mp4");
        ProjectAssetDescriptor asset2 = new ProjectAssetDescriptor(
                "art-2", "audio.mp3", "audio", "audio/mpeg",
                512, "sha256-def", 30.0, null, null,
                "s3://bucket/audio.mp3");
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of(asset1, asset2));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), eq("art-1"), eq("s3://bucket/video.mp4"), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), eq("art-2"), eq("s3://bucket/audio.mp3"), any()))
                .thenReturn(Optional.of("https://signed.example.com/audio.mp3?token=def"));

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        assertEquals("linked_assets", response.exportMode());
        assertTrue(response.manifest().security().containsSignedUrls());
        assertEquals(2, response.assets().assets().size());

        // Verify first asset
        ProjectExportAssetDto dto1 = response.assets().assets().get(0);
        assertEquals("art-1", dto1.assetId());
        assertEquals("video.mp4", dto1.filename());
        assertEquals("video", dto1.type());
        assertEquals("video/mp4", dto1.mimeType());
        assertEquals("https://signed.example.com/video.mp4?token=abc", dto1.downloadUrl());
        assertNull(dto1.storageRef());

        // Verify second asset
        ProjectExportAssetDto dto2 = response.assets().assets().get(1);
        assertEquals("art-2", dto2.assetId());
        assertEquals("audio", dto2.type());
        assertNotNull(dto2.downloadUrl());
    }

    @Test
    void linkedAssetsExportShouldGenerateUrlForEachAsset() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetDescriptor asset = new ProjectAssetDescriptor(
                "art-1", "file.mp4", "video", "video/mp4",
                1024, null, 5.0, 1920, 1080,
                "s3://bucket/file.mp4");
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of(asset));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), anyString(), anyString(), any()))
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

        ProjectAssetDescriptor asset = new ProjectAssetDescriptor(
                "art-1", "video.mp4", "video", "video/mp4",
                1024, null, 5.0, 1920, 1080,
                "s3://my-bucket/path/to/video.mp4");
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of(asset));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), anyString(), anyString(), any()))
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
    void linkedAssetsExportShouldFailWhenPortMissing() {
        ProjectExportService serviceWithoutPort = new ProjectExportService(tenantProjectService);
        serviceWithoutPort.setAuditPort(auditPort);
        // projectAssetExportPort is null

        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(UnsupportedOperationException.class, () -> {
            serviceWithoutPort.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldFailWhenPortNotAvailable() {
        ProjectExportService serviceWithPort = new ProjectExportService(tenantProjectService);
        serviceWithPort.setAuditPort(auditPort);
        serviceWithPort.setProjectAssetExportPort(projectAssetExportPort);
        when(projectAssetExportPort.isAvailable()).thenReturn(false);

        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(UnsupportedOperationException.class, () -> {
            serviceWithPort.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldFailClosedWhenAnyAssetSigningFails() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetDescriptor asset1 = new ProjectAssetDescriptor(
                "art-1", "video.mp4", "video", "video/mp4",
                1024, null, 5.0, 1920, 1080, "s3://bucket/video.mp4");
        ProjectAssetDescriptor asset2 = new ProjectAssetDescriptor(
                "art-2", "audio.mp3", "audio", "audio/mpeg",
                512, null, 30.0, null, null, "s3://bucket/audio.mp3");
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of(asset1, asset2));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), eq("art-1"), eq("s3://bucket/video.mp4"), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));
        when(projectAssetExportPort.generateSignedAssetUrl(eq("prj-1"), eq("art-2"), eq("s3://bucket/audio.mp3"), any()))
                .thenReturn(Optional.empty()); // Signing fails

        assertThrows(IllegalStateException.class, () -> {
            projectExportService.createExport("tenant-1", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });
    }

    @Test
    void linkedAssetsExportShouldRecordAssetCountInAudit() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectAssetDescriptor asset = new ProjectAssetDescriptor(
                "art-1", "video.mp4", "video", "video/mp4",
                1024, null, 5.0, 1920, 1080, "s3://bucket/video.mp4");
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of(asset));
        when(projectAssetExportPort.generateSignedAssetUrl(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));

        projectExportService.createExport("tenant-1", "prj-1",
                new ProjectExportRequest("linked_assets", 7200));

        verify(auditPort).record(anyString(), eq("PROJECT_EXPORT"), anyString(),
                anyString(), anyString(), argThat(m ->
                        Integer.valueOf(1).equals(m.get("assetCount")) &&
                        Integer.valueOf(7200).equals(m.get("ttlSeconds"))));
    }

    @Test
    void linkedAssetsExportShouldRecordAuditWithoutUrls() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of());
        // No assets = no signing calls

        projectExportService.createExport("tenant-1", "prj-1",
                new ProjectExportRequest("linked_assets", 3600));

        // Verify audit payload does not contain signed URLs
        verify(auditPort).record(anyString(), eq("PROJECT_EXPORT"), anyString(),
                anyString(), anyString(), argThat(m ->
                        "linked_assets".equals(m.get("mode")) &&
                        Integer.valueOf(0).equals(m.get("assetCount")) &&
                        !m.containsKey("signedUrls") &&
                        !m.containsKey("downloadUrl")));
    }

    @Test
    void metadataOnlyShouldStillNotNeedAssetPort() {
        ProjectExportService serviceWithoutPort = new ProjectExportService(tenantProjectService);
        serviceWithoutPort.setAuditPort(auditPort);

        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        ProjectExportResponse response = serviceWithoutPort.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("metadata_only", null));

        assertNotNull(response);
        assertEquals("metadata_only", response.exportMode());
        verifyNoInteractions(projectAssetExportPort);
    }

    @Test
    void wrongTenantShouldNotQueryOrSignAssets() {
        TenantContext.set("tenant-wrong");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        assertThrows(IllegalArgumentException.class, () -> {
            projectExportService.createExport("tenant-wrong", "prj-1",
                    new ProjectExportRequest("linked_assets", 3600));
        });

        verifyNoInteractions(projectAssetExportPort);
    }

    @Test
    void exportingNonExistentProjectShouldBeRejected() {
        TenantContext.set("tenant-1");
        when(tenantProjectService.getProject("prj-nonexistent")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            projectExportService.createExport("tenant-1", "prj-nonexistent",
                    new ProjectExportRequest("linked_assets", 3600));
        });

        verifyNoInteractions(projectAssetExportPort);
    }

    @Test
    void linkedAssetsExportShouldRejectTooLongTtl() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of());

        // Service clamps TTL to max
        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 100000));

        assertEquals("linked_assets", response.exportMode());
    }

    @Test
    void linkedAssetsExportShouldUseDefaultTtl() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of());

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", null));

        assertEquals("linked_assets", response.exportMode());
    }

    @Test
    void exportResponseShouldNotContainInternalUrls() {
        TenantContext.set("tenant-1");
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of());

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        String json = response.toString();
        assertFalse(json.contains("file://"));
        assertFalse(json.contains("/tmp/"));
        assertFalse(json.contains("storage-key"));
    }

    @Test
    void exportManifestShouldHaveCorrectSecurityFlags() {
        ProjectExportSecurityDto security = new ProjectExportSecurityDto(
                true, false, false, false, true, true, true);
        assertTrue(security.containsSignedUrls());
        assertFalse(security.containsMedia());
        assertTrue(security.promptRedacted());
        assertTrue(security.storageRefsRedacted());
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
    void exportProjectShouldMatchSourceProject() {
        TenantContext.set("tenant-1");
        Instant now = Instant.now();
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "My Project",
                "My Description", "ACTIVE", now);
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);
        when(projectAssetExportPort.isAvailable()).thenReturn(true);
        when(projectAssetExportPort.listProjectAssets("prj-1")).thenReturn(List.of());

        ProjectExportResponse response = projectExportService.createExport(
                "tenant-1", "prj-1", new ProjectExportRequest("linked_assets", 3600));

        assertEquals("prj-1", response.project().projectId());
        assertEquals("tenant-1", response.project().tenantId());
        assertEquals("My Project", response.project().name());
    }
}
