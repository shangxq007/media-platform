package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.dto.ProjectExportRequest;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectExportServiceTest {

    private TenantProjectService projects;
    private ProjectExportService service;

    @BeforeEach
    void setUp() {
        projects = mock(TenantProjectService.class);
        service = new ProjectExportService(projects, mock(AuditPort.class));
        TenantContext.set("tenant-1");
        when(projects.getProject("project-1")).thenReturn(new ProjectResponse(
                "project-1", "tenant-1", "Project", "Description", "ACTIVE", Instant.now()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void metadataOnlyExportRemainsRedacted() {
        var response = service.createExport(
                "tenant-1", "project-1", new ProjectExportRequest("metadata_only", null));

        assertEquals("metadata_only", response.exportMode());
        assertFalse(response.manifest().security().containsSignedUrls());
        assertEquals(0, response.assets().assets().size());
    }

    @Test
    void linkedAssetsFailsClosedWithoutAnExactArtifactScope() {
        assertThrows(UnsupportedOperationException.class, () -> service.createExport(
                "tenant-1", "project-1", new ProjectExportRequest("linked_assets", 3600)));
    }

    @Test
    void wrongTenantStillFailsBeforeModeEvaluation() {
        TenantContext.set("tenant-2");
        assertThrows(IllegalArgumentException.class, () -> service.createExport(
                "tenant-2", "project-1", new ProjectExportRequest("linked_assets", 3600)));
    }
}
