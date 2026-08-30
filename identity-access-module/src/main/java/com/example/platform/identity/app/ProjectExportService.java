package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for creating project exports.
 * Supports metadata_only and linked_assets modes.
 */
@Service
public class ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportService.class);
    private final TenantProjectService tenantProjectService;
    private final AuditPort auditPort;

    public ProjectExportService(TenantProjectService tenantProjectService,
                                @Autowired(required = false) AuditPort auditPort) {
        this.tenantProjectService = tenantProjectService;
        this.auditPort = auditPort;
    }

    public ProjectExportResponse createExport(String tenantId, String projectId,
                                               ProjectExportRequest request) {
        assertTenantAccess(tenantId);

        ProjectResponse projectResp = tenantProjectService.getProject(projectId);
        if (projectResp == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        if (!tenantId.equals(projectResp.tenantId())) {
            throw new IllegalArgumentException("Project not found for tenant: " + tenantId);
        }

        String exportId = Ids.newId("exp");
        Instant now = Instant.now();
        String exportedBy = "tenant:" + tenantId;
        String auditEventId = Ids.newId("aud");

        ProjectExportResponse response;
        if (ProjectExportRequest.MODE_LINKED_ASSETS.equals(request.mode())) {
            throw new UnsupportedOperationException(
                    "linked_assets export is unavailable until it can use an exact scoped Artifact access request");
        } else {
            response = buildMetadataOnlyExport(projectResp, exportId, now, exportedBy);
        }

        recordExportAudit(auditEventId, tenantId, projectId, exportId, exportedBy,
                request.mode(), response.assets().assets().size(),
                ProjectExportRequest.MODE_LINKED_ASSETS.equals(request.mode())
                        ? (request.signedUrlTtlSeconds() != null ? request.signedUrlTtlSeconds() : 3600) : null);

        return response;
    }

    private ProjectExportResponse buildMetadataOnlyExport(ProjectResponse projectResp,
                                                            String exportId, Instant now,
                                                            String exportedBy) {
        return new ProjectExportResponse(
                "project-export-v1", exportId, ProjectExportRequest.MODE_METADATA_ONLY, now,
                buildManifest(exportId, now, exportedBy, ProjectExportRequest.MODE_METADATA_ONLY,
                        false, 0, null),
                buildProjectDto(projectResp),
                new ProjectExportAssetsDto("project-export-v1", "metadata_only", List.of(), null),
                buildTimelineDto(), buildRenderDto(), buildEffectsDto(),
                buildOutputsDto(), buildAuditDto(exportId, now, exportedBy)
        );
    }

    private ProjectExportManifestDto buildManifest(String exportId, Instant now,
                                                     String exportedBy, String mode,
                                                     boolean containsSignedUrls,
                                                     int assetCount, Integer ttlSeconds) {
        return new ProjectExportManifestDto(
                "project-export-v1", "project-export-v1", exportId, mode, now, exportedBy,
                Map.of("minPlatformVersion", "1.0.0", "effectTaxonomyVersion", "v1",
                        "spatialPlanVersion", "v1", "otioSchema", "Timeline.1"),
                new ProjectExportSecurityDto(containsSignedUrls, false, false, false,
                        true, true, true),
                new ProjectExportManifestAssetsDto(mode, assetCount, 0),
                Map.of("algorithm", "sha256", "file", "checksums/sha256sums.txt")
        );
    }

    private ProjectExportProjectDto buildProjectDto(ProjectResponse project) {
        return new ProjectExportProjectDto(
                project.id(), project.tenantId(), project.name(),
                project.description(), project.createdAt(), project.createdAt(),
                project.status() != null ? project.status() : "ACTIVE"
        );
    }

    private ProjectExportTimelineDto buildTimelineDto() {
        return new ProjectExportTimelineDto("project-export-v1", List.of(), 0);
    }

    private ProjectExportRenderDto buildRenderDto() {
        return new ProjectExportRenderDto("project-export-v1", Map.of(), Map.of(), "v1");
    }

    private ProjectExportEffectsDto buildEffectsDto() {
        return new ProjectExportEffectsDto("project-export-v1", "v1", List.of());
    }

    private ProjectExportOutputsDto buildOutputsDto() {
        return new ProjectExportOutputsDto("project-export-v1", 0, List.of());
    }

    private ProjectExportAuditDto buildAuditDto(String exportId, Instant now, String exportedBy) {
        return new ProjectExportAuditDto("project-export-v1", Ids.newId("aud"), now, exportedBy,
                "PROJECT_EXPORT");
    }

    private void recordExportAudit(String auditEventId, String tenantId, String projectId,
                                    String exportId, String exportedBy, String mode,
                                    int assetCount, Integer ttlSeconds) {
        try {
            if (auditPort != null) {
                auditPort.record("TENANT", "PROJECT_EXPORT", "PROJECT_EXPORT",
                        "project", projectId,
                        Map.of("exportId", exportId, "mode", mode,
                                "tenantId", tenantId, "assetCount", assetCount,
                                "ttlSeconds", ttlSeconds != null ? ttlSeconds : 0));
            }
        } catch (Exception e) {
            log.warn("Failed to record export audit event: {}", e.getMessage());
        }
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant: " + tenantId);
        }
    }
}
