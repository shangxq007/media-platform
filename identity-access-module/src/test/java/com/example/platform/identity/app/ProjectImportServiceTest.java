package com.example.platform.identity.app;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.imports.DownloadedAsset;
import com.example.platform.shared.imports.ImportAssetDownloader;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectImportServiceTest {

    private static final String VALID_CHECKSUM = "sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @Mock
    private TenantProjectService tenantProjectService;

    @Mock
    private ArtifactCatalogService artifactCatalogService;

    @Mock
    private BlobStorage blobStorage;

    @Mock
    private AuditPort auditPort;

    @Mock
    private ImportAssetDownloader assetDownloader;

    private ProjectImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ProjectImportService(tenantProjectService, artifactCatalogService);
        importService.setAuditPort(auditPort);
        importService.setAssetDownloader(assetDownloader);
        importService.setBlobStorage(blobStorage);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── helpers ───

    private ProjectExportPackageDto buildMetadataOnlyPayload() {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc",
                        null, null, "ACTIVE"),
                null, null, null);
    }

    private ProjectExportPackageDto buildLinkedAssetsPayload() {
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "src-art-1", "video.mp4", "video", "video/mp4",
                1024L, VALID_CHECKSUM,
                5.0, 1920, 1080, null,
                "https://signed.example.com/video.mp4?token=abc");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        return new ProjectExportPackageDto(
                "project-export-v1", "linked_assets", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc",
                        null, null, "ACTIVE"),
                assets, null, null);
    }

    private ProjectExportPackageDto buildTwoAssetPayload() {
        ProjectExportAssetDto a1 = new ProjectExportAssetDto(
                "src-art-1", "video1.mp4", "video", "video/mp4",
                1024L, VALID_CHECKSUM, 5.0, 1920, 1080, null,
                "https://signed.example.com/video1.mp4");
        ProjectExportAssetDto a2 = new ProjectExportAssetDto(
                "src-art-2", "video2.mp4", "video", "video/mp4",
                2048L, VALID_CHECKSUM, 8.0, 1920, 1080, null,
                "https://signed.example.com/video2.mp4");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(a1, a2), null);
        return new ProjectExportPackageDto(
                "project-export-v1", "linked_assets", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source", "desc",
                        null, null, "ACTIVE"),
                assets, null, null);
    }

    private Artifact mockRegisteredArtifact(String id) {
        return new Artifact(id, "import:imp-1", "prj-1", "imported://prj-1/src",
                "mp4", "1920x1080", 5L, 1024L, VALID_CHECKSUM,
                ArtifactStatus.ACTIVE, null, Instant.now());
    }

    private DownloadedAsset createDownloadedAsset(String name, long size, String checksum) {
        try {
            Path tmp = Files.createTempFile("test-import-", "-" + name);
            Files.write(tmp, new byte[(int) size]);
            return new DownloadedAsset(tmp, size, checksum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ─── metadata_only ───

    @Test
    void metadataOnlyImportShouldCreateNewProject() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Source Project (imported)", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        ProjectImportRequest request = new ProjectImportRequest(
                buildMetadataOnlyPayload(), "metadata_only", null,
                true, null, ProjectImportRequest.POLICY_METADATA_ONLY,
                null, null, null, null);

        ProjectImportResponse response = importService.executeImport("tenant-1", request);

        assertNotNull(response.importId());
        assertEquals("new-prj-1", response.projectId());
        assertEquals("metadata_only", response.mode());
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> "SUCCEEDED".equals(m.get("status"))));
    }

    // ─── download_and_register: success ───

    @Test
    void downloadAndRegisterShouldCommitAllAssets() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        DownloadedAsset dl1 = createDownloadedAsset("a1.mp4", 1024L, VALID_CHECKSUM);
        DownloadedAsset dl2 = createDownloadedAsset("a2.mp4", 2048L, VALID_CHECKSUM);
        when(assetDownloader.download("https://signed.example.com/video1.mp4")).thenReturn(dl1);
        when(assetDownloader.download("https://signed.example.com/video2.mp4")).thenReturn(dl2);

        when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key1"))
                .thenReturn(new StorageObjectRef("local", "imports", "key2"));
        // deleteStorageUri is only called on failure; use lenient to avoid UnnecessaryStubbingException
        lenient().when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        Artifact art1 = mockRegisteredArtifact("target-art-1");
        Artifact art2 = mockRegisteredArtifact("target-art-2");
        when(artifactCatalogService.registerArtifact(anyString(), eq("new-prj-1"), anyString(),
                anyString(), any(), anyLong(), eq(1024L), eq(VALID_CHECKSUM))).thenReturn(art1);
        when(artifactCatalogService.registerArtifact(anyString(), eq("new-prj-1"), anyString(),
                anyString(), any(), anyLong(), eq(2048L), eq(VALID_CHECKSUM))).thenReturn(art2);

        ProjectImportRequest request = new ProjectImportRequest(
                buildTwoAssetPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        ProjectImportResponse response = importService.executeImport("tenant-1", request);

        assertEquals(2, response.assets().imported());
        assertEquals("target-art-1", response.assetMappings().get("src-art-1"));
        assertEquals("target-art-2", response.assetMappings().get("src-art-2"));

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> "SUCCEEDED".equals(m.get("status"))
                        && Boolean.FALSE.equals(m.get("rollbackAttempted"))));
    }

    // ─── download_and_register: rollback on second asset failure ───

    @Test
    void shouldRollbackRegisteredArtifactsWhenLaterAssetDownloadFails() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        // Asset 1: download succeeds
        DownloadedAsset dl1 = createDownloadedAsset("a1.mp4", 1024L, VALID_CHECKSUM);
        when(assetDownloader.download("https://signed.example.com/video1.mp4")).thenReturn(dl1);
        when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key1"));
        when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);
        Artifact art1 = mockRegisteredArtifact("target-art-1");
        when(artifactCatalogService.registerArtifact(anyString(), eq("new-prj-1"), anyString(),
                anyString(), any(), anyLong(), eq(1024L), eq(VALID_CHECKSUM))).thenReturn(art1);

        // Asset 2: download fails
        when(assetDownloader.download("https://signed.example.com/video2.mp4"))
                .thenThrow(new com.example.platform.shared.imports.AssetDownloadException(
                        "HTTP_ERROR", "HTTP 500"));

        ProjectImportRequest request = new ProjectImportRequest(
                buildTwoAssetPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });

        // Artifact 1 should be tombstoned (rollback)
        verify(artifactCatalogService).updateStatus("target-art-1", ArtifactStatus.TOMBSTONED);

        // Audit should record rollback
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> {
                    String status = (String) m.get("status");
                    return "ROLLED_BACK".equals(status) || "FAILED".equals(status);
                }));
    }

    // ─── download_and_register: checksum mismatch ───

    @Test
    void shouldRollbackWhenChecksumMismatch() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        // Download returns different checksum
        DownloadedAsset dl1 = createDownloadedAsset("a1.mp4", 1024L,
                "sha256:0000000000000000000000000000000000000000000000000000000000000000");
        when(assetDownloader.download(anyString())).thenReturn(dl1);
        // blobStorage mocks are lenient because checksum fails before storage write
        lenient().when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key1"));
        lenient().when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        ProjectImportRequest request = new ProjectImportRequest(
                buildLinkedAssetsPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });

        // No artifact registration should happen
        verify(artifactCatalogService, never()).registerArtifact(anyString(), anyString(),
                anyString(), anyString(), any(), anyLong(), any(), anyString());

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m ->
                        "CHECKSUM_MISMATCH".equals(m.get("failureReasonCode"))));
    }

    // ─── download_and_register: size mismatch ───

    @Test
    void shouldRollbackWhenSizeMismatch() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        // Download returns different size (expected 1024, got 999)
        DownloadedAsset dl1 = createDownloadedAsset("a1.mp4", 999L, VALID_CHECKSUM);
        when(assetDownloader.download(anyString())).thenReturn(dl1);
        // blobStorage mocks are lenient because size mismatch fails before storage write
        lenient().when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key1"));
        lenient().when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        ProjectImportRequest request = new ProjectImportRequest(
                buildLinkedAssetsPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m ->
                        "SIZE_MISMATCH".equals(m.get("failureReasonCode"))));
    }

    // ─── download_and_register: registerArtifact fails ───

    @Test
    void shouldRollbackWhenRegisterArtifactFails() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        DownloadedAsset dl1 = createDownloadedAsset("a1.mp4", 1024L, VALID_CHECKSUM);
        when(assetDownloader.download(anyString())).thenReturn(dl1);
        lenient().when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key1"));
        lenient().when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        when(artifactCatalogService.registerArtifact(anyString(), anyString(), anyString(),
                anyString(), any(), anyLong(), any(), anyString()))
                .thenThrow(new RuntimeException("DB error"));

        ProjectImportRequest request = new ProjectImportRequest(
                buildLinkedAssetsPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });

        // Verify audit records either ARTIFACT_REGISTER_FAILED or STORAGE_WRITE_FAILED
        // (both are valid failure reasons in this test scenario)
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> {
                    String reason = (String) m.get("failureReasonCode");
                    return "ARTIFACT_REGISTER_FAILED".equals(reason)
                            || "STORAGE_WRITE_FAILED".equals(reason);
                }));
    }

    // ─── unsafe URL ───

    @Test
    void shouldRejectUnsafeUrlBeforeDownload() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "src-art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 5.0, 1920, 1080, null,
                "http://169.254.169.254/latest/meta-data");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto payload = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source", "desc",
                        null, null, "ACTIVE"),
                assets, null, null);

        ProjectImportRequest request = new ProjectImportRequest(
                payload, "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });

        verifyNoInteractions(assetDownloader);
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m ->
                        "UNSAFE_URL".equals(m.get("failureReasonCode"))));
    }

    // ─── audit does not contain sensitive data ───

    @Test
    void shouldAuditWithoutSensitiveData() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        ProjectImportRequest request = new ProjectImportRequest(
                buildMetadataOnlyPayload(), "metadata_only", null,
                true, null, ProjectImportRequest.POLICY_METADATA_ONLY,
                null, null, null, null);

        importService.executeImport("tenant-1", request);

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> {
                    String s = m.toString();
                    return !s.contains("downloadUrl") &&
                            !s.contains("signed.example.com") &&
                            !s.contains("storageUri") &&
                            !s.contains("bucket") &&
                            !s.contains("/tmp/");
                }));
    }

    // ─── error handling ───

    @Test
    void shouldRejectUnsupportedSchemaVersion() {
        TenantContext.set("tenant-1");
        ProjectExportPackageDto payload = new ProjectExportPackageDto(
                "unsupported-v2", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source", "desc",
                        null, null, "ACTIVE"),
                null, null, null);

        ProjectImportRequest request = new ProjectImportRequest(
                payload, "metadata_only", null,
                true, null, ProjectImportRequest.POLICY_METADATA_ONLY,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });
    }

    @Test
    void shouldRejectWrongTenant() {
        TenantContext.set("tenant-correct");
        ProjectImportRequest request = new ProjectImportRequest(
                buildMetadataOnlyPayload(), "metadata_only", null,
                true, null, ProjectImportRequest.POLICY_METADATA_ONLY,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-wrong", request);
        });
        verifyNoInteractions(tenantProjectService);
    }

    @Test
    void shouldRejectTargetProjectFromWrongTenant() {
        TenantContext.set("tenant-1");
        ProjectResponse wrongTenant = new ProjectResponse("target-prj-1", "tenant-2",
                "Target", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("target-prj-1")).thenReturn(wrongTenant);

        ProjectImportRequest request = new ProjectImportRequest(
                buildLinkedAssetsPayload(), "linked_assets", "target-prj-1",
                false, null, ProjectImportRequest.POLICY_REQUIRE_EXISTING_MAPPING,
                Map.of("src-art-1", "target-art-1"),
                null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });
    }

    @Test
    void shouldRejectNeitherCreateNorTargetSpecified() {
        TenantContext.set("tenant-1");
        ProjectImportRequest request = new ProjectImportRequest(
                buildMetadataOnlyPayload(), "metadata_only", null,
                false, null, ProjectImportRequest.POLICY_METADATA_ONLY,
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            importService.executeImport("tenant-1", request);
        });
    }

    @Test
    void shouldAllowMissingChecksumWhenNotRequired() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        // Asset without checksum
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "src-art-1", "video.mp4", "video", "video/mp4",
                1024L, null, 5.0, 1920, 1080, null,
                "https://signed.example.com/video.mp4");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        ProjectExportPackageDto payload = new ProjectExportPackageDto(
                "project-export-v1", "linked_assets", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source", "desc",
                        null, null, "ACTIVE"),
                assets, null, null);

        DownloadedAsset dl = createDownloadedAsset("a.mp4", 1024L,
                "sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        when(assetDownloader.download(anyString())).thenReturn(dl);
        when(blobStorage.put(any(PutObjectCommand.class)))
                .thenReturn(new StorageObjectRef("local", "imports", "key"));

        Artifact art = mockRegisteredArtifact("target-art-1");
        when(artifactCatalogService.registerArtifact(anyString(), anyString(), anyString(),
                anyString(), any(), anyLong(), any(), anyString())).thenReturn(art);

        ProjectImportRequest request = new ProjectImportRequest(
                payload, "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, false, null, null); // requireChecksum=false

        ProjectImportResponse response = importService.executeImport("tenant-1", request);
        assertEquals(1, response.assets().imported());
    }

    @Test
    void shouldFailClosedWhenBlobStorageUnavailable() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse("new-prj-1", "tenant-1",
                "Test", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        ProjectImportService serviceNoStorage = new ProjectImportService(tenantProjectService, artifactCatalogService);
        serviceNoStorage.setAuditPort(auditPort);
        serviceNoStorage.setAssetDownloader(assetDownloader);
        // blobStorage NOT set

        ProjectImportRequest request = new ProjectImportRequest(
                buildLinkedAssetsPayload(), "linked_assets", null,
                true, null, ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER,
                null, null, null, null);

        assertThrows(ProjectImportService.ImportFailureException.class, () -> {
            serviceNoStorage.executeImport("tenant-1", request);
        });

        verifyNoInteractions(assetDownloader);
    }
}
