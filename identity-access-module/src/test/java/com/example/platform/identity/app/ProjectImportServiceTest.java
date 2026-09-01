package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.dto.ProjectExportAssetDto;
import com.example.platform.identity.api.dto.ProjectExportAssetsDto;
import com.example.platform.identity.api.dto.ProjectExportPackageDto;
import com.example.platform.identity.api.dto.ProjectExportProjectDto;
import com.example.platform.identity.api.dto.ProjectImportRequest;
import com.example.platform.identity.api.dto.ProjectImportResponse;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectImportServiceTest {

    private static final String VALID_CHECKSUM =
            "sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @Mock
    private TenantProjectService tenantProjectService;

    @Mock
    private AuditPort auditPort;

    private ProjectImportService importService;

    @BeforeEach
    void setUp() {
        importService = new ProjectImportService(tenantProjectService, auditPort);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void metadataOnlyImportCreatesNewProject() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse(
                "new-prj-1", "tenant-1", "Source Project (imported)", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        ProjectImportResponse response = importService.executeImport(
                "tenant-1", request(metadataOnlyPayload(), ProjectImportRequest.POLICY_METADATA_ONLY));

        assertNotNull(response.importId());
        assertEquals("new-prj-1", response.projectId());
        assertEquals("metadata_only", response.mode());
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(payload -> "SUCCEEDED".equals(payload.get("status"))));
    }

    @Test
    void downloadAndRegisterFailsClosedBeforeProjectOrStorageSideEffects() {
        TenantContext.set("tenant-1");

        assertThrows(UnsupportedOperationException.class, () -> importService.executeImport(
                "tenant-1", request(linkedAssetsPayload(), ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER)));

        verifyNoInteractions(tenantProjectService);
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(payload ->
                        "FAILED".equals(payload.get("status"))
                                && Boolean.FALSE.equals(payload.get("rollbackAttempted"))));
    }

    @Test
    void auditDoesNotContainStorageCoordinatesOrDownloadUrls() {
        TenantContext.set("tenant-1");
        ProjectResponse created = new ProjectResponse(
                "new-prj-1", "tenant-1", "Source Project (imported)", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.createProject(eq("tenant-1"), any())).thenReturn(created);

        importService.executeImport(
                "tenant-1", request(metadataOnlyPayload(), ProjectImportRequest.POLICY_METADATA_ONLY));

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(payload -> {
                    String serialized = payload.toString();
                    return !serialized.contains("downloadUrl")
                            && !serialized.contains("storageUri")
                            && !serialized.contains("bucket")
                            && !serialized.contains("/tmp/");
                }));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        TenantContext.set("tenant-1");
        ProjectExportPackageDto payload = new ProjectExportPackageDto(
                "unsupported-v2", "metadata_only", null,
                new ProjectExportProjectDto(
                        "src-prj-1", "src-tenant", "Source", "desc", null, null, "ACTIVE"),
                null, null, null);

        assertThrows(IllegalArgumentException.class, () -> importService.executeImport(
                "tenant-1", request(payload, ProjectImportRequest.POLICY_METADATA_ONLY)));
    }

    @Test
    void rejectsWrongTenantBeforeProjectAccess() {
        TenantContext.set("tenant-correct");

        assertThrows(IllegalArgumentException.class, () -> importService.executeImport(
                "tenant-wrong", request(metadataOnlyPayload(), ProjectImportRequest.POLICY_METADATA_ONLY)));

        verifyNoInteractions(tenantProjectService);
    }

    @Test
    void rejectsTargetProjectFromWrongTenant() {
        TenantContext.set("tenant-1");
        ProjectResponse wrongTenant = new ProjectResponse(
                "target-prj-1", "tenant-2", "Target", "desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("target-prj-1")).thenReturn(wrongTenant);
        ProjectImportRequest request = new ProjectImportRequest(
                linkedAssetsPayload(), "linked_assets", "target-prj-1", false, null,
                ProjectImportRequest.POLICY_REQUIRE_EXISTING_MAPPING,
                Map.of("src-art-1", "target-art-1"), null, null, null);

        assertThrows(IllegalArgumentException.class, () -> importService.executeImport("tenant-1", request));
    }

    @Test
    void rejectsImportWithoutCreateOrTarget() {
        TenantContext.set("tenant-1");
        ProjectImportRequest request = new ProjectImportRequest(
                metadataOnlyPayload(), "metadata_only", null, false, null,
                ProjectImportRequest.POLICY_METADATA_ONLY, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> importService.executeImport("tenant-1", request));
    }

    private static ProjectImportRequest request(ProjectExportPackageDto payload, String policy) {
        return new ProjectImportRequest(
                payload, payload.exportMode(), null, true, null, policy, null, null, null, null);
    }

    private static ProjectExportPackageDto metadataOnlyPayload() {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto(
                        "src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                null, null, null);
    }

    private static ProjectExportPackageDto linkedAssetsPayload() {
        ProjectExportAssetDto asset = new ProjectExportAssetDto(
                "src-art-1", "video.mp4", "video", "video/mp4", 1024L, VALID_CHECKSUM,
                5.0, 1920, 1080, null, "https://signed.example.com/video.mp4?token=redacted");
        ProjectExportAssetsDto assets = new ProjectExportAssetsDto(
                "project-export-v1", "linked_assets", List.of(asset), null);
        return new ProjectExportPackageDto(
                "project-export-v1", "linked_assets", null,
                new ProjectExportProjectDto(
                        "src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                assets, null, null);
    }
}
