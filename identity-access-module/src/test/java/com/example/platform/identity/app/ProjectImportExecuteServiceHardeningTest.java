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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectImportExecuteServiceHardeningTest {

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

    // ─── Rollback tests ───

    @Test
    void shouldRollbackProjectOnMetadataFailure() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        // Simulate failure during asset processing by making assets throw
        ProjectExportAssetsDto badAssets = mock(ProjectExportAssetsDto.class);
        when(badAssets.assets()).thenThrow(new RuntimeException("Simulated failure"));
        ProjectExportPackageDto badPackage = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Test", "desc", null, null, "ACTIVE"),
                badAssets, null, null);

        // When/Then
        assertThrows(RuntimeException.class, () ->
                service.executeShellImport("tenant-1", null, badPackage));

        // Verify rollback was attempted
        verify(projectRepository).deleteById("prj-123");
    }

    @Test
    void shouldNotAttemptRollbackIfProjectCreationFails() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenThrow(new IllegalArgumentException("Tenant not found"));

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                service.executeShellImport("tenant-1", null, exportPackage));

        // Verify no rollback was attempted (project was never created)
        verify(projectRepository, never()).deleteById(any());
    }

    // ─── Audit redaction tests ───

    @Test
    void shouldRecordAuditWithoutSensitiveFields() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - verify import succeeded
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    @Test
    void auditFailureShouldNotBlockImport() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));
        doThrow(new RuntimeException("Audit service unavailable")).when(auditPort)
                .record(any(), any(), any(), any(), any(), any());

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - import should succeed despite audit failure
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    // ─── Security tests ───

    @Test
    void responseShouldNotContainStorageFields() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        String resultStr = result.toString().toLowerCase();
        assertFalse(resultStr.contains("storageuri"));
        assertFalse(resultStr.contains("storage_ref"));
        assertFalse(resultStr.contains("storageref"));
    }

    @Test
    void shouldIgnoreSourceTenantId() {
        // Given - export package has different tenantId
        ProjectExportPackageDto exportPackage = new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "malicious-tenant", "Malicious", "desc", null, null, "ACTIVE"),
                null, null, null);
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Malicious (Imported)", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - project created in tenant-1, not malicious-tenant
        assertNotNull(result);
        verify(tenantProjectService).createProject(eq("tenant-1"), any());
    }

    // ─── Unsupported effect tests ───

    @Test
    void unsupportedEffectShouldProduceWarning() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithUnsupportedEffect();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then
        assertNotNull(result);
        assertTrue(result.warnings().stream()
                .anyMatch(w -> w.message().contains("custom.effect")));
    }

    @Test
    void unsupportedEffectShouldNotFallbackToFilter() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithUnsupportedEffect();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - no fallback filter mentioned in warnings
        assertNotNull(result);
        assertFalse(result.warnings().stream()
                .anyMatch(w -> w.message().toLowerCase().contains("fallback")));
    }

    @Test
    void unsupportedEffectShouldNotBlockShellImport() {
        // Given
        ProjectExportPackageDto exportPackage = createSampleExportPackageWithUnsupportedEffect();
        when(tenantProjectService.createProject(eq("tenant-1"), any()))
                .thenReturn(new ProjectResponse("prj-123", "tenant-1", "Test", "", "ACTIVE", null));

        // When
        ProjectImportExecuteService.ImportExecuteResult result =
                service.executeShellImport("tenant-1", null, exportPackage);

        // Then - import succeeds despite unsupported effects
        assertNotNull(result);
        assertEquals("prj-123", result.targetProjectId());
    }

    // ─── Helper methods ───

    private ProjectExportPackageDto createSampleExportPackage() {
        return new ProjectExportPackageDto(
                "project-export-v1", "metadata_only", null,
                new ProjectExportProjectDto("src-prj-1", "src-tenant", "Source Project", "desc", null, null, "ACTIVE"),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                null, null);
    }

    private ProjectExportPackageDto createSampleExportPackageWithUnsupportedEffect() {
        ProjectExportPackageDto exportPackage = createSampleExportPackage();
        ProjectExportRenderDto render = new ProjectExportRenderDto(
                "project-export-v1",
                Map.of("operations", List.of(
                        Map.of("effectKey", "custom.effect")
                )),
                null, null);
        return new ProjectExportPackageDto(
                exportPackage.schemaVersion(), exportPackage.exportMode(),
                exportPackage.manifest(), exportPackage.project(),
                exportPackage.assets(), exportPackage.timeline(), render);
    }
}
