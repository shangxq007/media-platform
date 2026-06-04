package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.asset.AssetDownloadUrlPort;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.export.ProjectAssetListingPort;
import com.example.platform.shared.export.ProjectAssetRef;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for creating project exports.
 * Supports metadata_only and linked_assets modes.
 */
@Service
public class ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportService.class);
    private static final Duration DEFAULT_SIGNED_URL_TTL = Duration.ofHours(1);
    private static final Duration MAX_SIGNED_URL_TTL = Duration.ofHours(24);

    private final TenantProjectService tenantProjectService;
    private AuditPort auditPort;
    private ProjectAssetListingPort projectAssetListingPort;
    private AssetDownloadUrlPort assetDownloadUrlPort;

    public ProjectExportService(TenantProjectService tenantProjectService) {
        this.tenantProjectService = tenantProjectService;
    }

    @Autowired(required = false)
    public void setAuditPort(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    @Autowired(required = false)
    public void setProjectAssetListingPort(ProjectAssetListingPort projectAssetListingPort) {
        this.projectAssetListingPort = projectAssetListingPort;
    }

    @Autowired(required = false)
    public void setAssetDownloadUrlPort(AssetDownloadUrlPort assetDownloadUrlPort) {
        this.assetDownloadUrlPort = assetDownloadUrlPort;
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
            Integer ttl = request.signedUrlTtlSeconds();
            response = buildLinkedAssetsExport(tenantId, projectId, projectResp, exportId,
                    now, exportedBy, ttl != null ? ttl : 3600);
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

    private ProjectExportResponse buildLinkedAssetsExport(String tenantId, String projectId,
                                                           ProjectResponse projectResp,
                                                           String exportId, Instant now,
                                                           String exportedBy,
                                                           int ttlSeconds) {
        // Check if asset listing port is available
        if (projectAssetListingPort == null || !projectAssetListingPort.isAvailable()) {
            log.error("linked_assets export requested but ProjectAssetListingPort is not available");
            throw new UnsupportedOperationException(
                    "linked_assets export requires a configured ProjectAssetListingPort and AssetDownloadUrlPort. " +
                    "Please ensure storage signing is configured.");
        }
        if (assetDownloadUrlPort == null || !assetDownloadUrlPort.isAvailable()) {
            log.error("linked_assets export requested but AssetDownloadUrlPort is not available");
            throw new UnsupportedOperationException(
                    "linked_assets export requires a configured AssetDownloadUrlPort. " +
                    "Please ensure storage signing is configured.");
        }

        Duration ttl = computeTtl(ttlSeconds);
        Instant expiresAt = now.plus(ttl);

        // Query project assets via port (tenant-scoped)
        List<ProjectAssetRef> assets = projectAssetListingPort.listAssets(tenantId, projectId);
        log.info("linked_assets export: found {} assets for project {}", assets.size(), projectId);

        // Build asset DTOs with signed URLs (fail-closed)
        List<ProjectExportAssetDto> assetDtos = new ArrayList<>();
        for (ProjectAssetRef asset : assets) {
            String signedUrl = null;
            if (asset.storageUri() != null && !asset.storageUri().isBlank()) {
                Optional<String> urlOpt = assetDownloadUrlPort.generateSignedUrl(
                        asset.assetId(), asset.storageUri(), ttl);
                if (urlOpt.isPresent()) {
                    signedUrl = urlOpt.get();
                } else {
                    // Fail closed
                    log.error("Failed to generate signed URL for asset {}", asset.assetId());
                    throw new IllegalStateException(
                            "Failed to generate signed URL for asset: " + asset.assetId() +
                            ". Export aborted (fail-closed policy).");
                }
            }

            assetDtos.add(new ProjectExportAssetDto(
                    asset.assetId(),
                    asset.filename(),
                    asset.assetType(),
                    asset.mimeType(),
                    asset.sizeBytes(),
                    asset.checksum(),
                    asset.durationMs() != null ? asset.durationMs().doubleValue() / 1000.0 : null,
                    asset.width(),
                    asset.height(),
                    null, // storageRef never exposed
                    signedUrl
            ));
        }

        return new ProjectExportResponse(
                "project-export-v1", exportId, ProjectExportRequest.MODE_LINKED_ASSETS, now,
                buildManifest(exportId, now, exportedBy, ProjectExportRequest.MODE_LINKED_ASSETS,
                        true, assetDtos.size(), ttlSeconds),
                buildProjectDto(projectResp),
                new ProjectExportAssetsDto("project-export-v1", "linked_assets", assetDtos, null),
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

    private Duration computeTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) return DEFAULT_SIGNED_URL_TTL;
        Duration requested = Duration.ofSeconds(ttlSeconds);
        return requested.compareTo(MAX_SIGNED_URL_TTL) > 0 ? MAX_SIGNED_URL_TTL : requested;
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
