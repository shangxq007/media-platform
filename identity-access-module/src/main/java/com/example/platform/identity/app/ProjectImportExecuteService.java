package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.io.ChecksumFormat;
import com.example.platform.shared.security.SafeDownloadUrlValidator;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for executing project imports from export packages.
 *
 * <p>Currently supports {@code shell_only} mode which creates a project shell
 * with metadata but does NOT download signed URLs, copy media, or register artifacts.
 *
 * <p>Security:
 * <ul>
 *   <li>Tenant boundary enforced via {@code assertTenantAccess}</li>
 *   <li>Zip contents treated as untrusted</li>
 *   <li>Source project metadata used for naming only, not for permissions</li>
 *   <li>No signed URLs downloaded or persisted</li>
 *   <li>No storageUri written to response or audit</li>
 * </ul>
 */
@Service
public class ProjectImportExecuteService {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportExecuteService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUPPORTED_SCHEMA_VERSION = "project-export-v1";
    private static final String MODE_SHELL_ONLY = "shell_only";

    private final TenantProjectService tenantProjectService;
    private final ProjectRepository projectRepository;
    private final ProjectImportMetadataRepository metadataRepository;
    private final MetadataScrubber metadataScrubber;
    private AuditPort auditPort;

    public ProjectImportExecuteService(TenantProjectService tenantProjectService,
                                       ProjectRepository projectRepository,
                                       ProjectImportMetadataRepository metadataRepository,
                                       MetadataScrubber metadataScrubber) {
        this.tenantProjectService = tenantProjectService;
        this.projectRepository = projectRepository;
        this.metadataRepository = metadataRepository;
        this.metadataScrubber = metadataScrubber;
    }

    @Autowired(required = false)
    public void setAuditPort(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * Result of import execution.
     */
    public record ImportExecuteResult(
            String importId,
            String targetProjectId,
            String mode,
            ProjectImportAssetSummaryDto assetSummary,
            List<ProjectImportAssetMappingDto> assetMappings,
            List<ImportPreviewIssueDto> warnings,
            ImportMetadataSummaryDto metadata
    ) {}

    /**
     * Execute a shell_only import: create a project shell from export package metadata.
     *
     * <p>Does NOT download signed URLs, copy media, or register artifacts.
     * All assets are marked as needs_upload in the response.
     *
     * <p>Transactional: project creation, asset mapping, and audit all occur in one transaction.
     * If any step fails, the transaction rolls back and no project shell is left behind.
     *
     * <p>Audit failure policy: audit recording failures are caught and logged but do NOT
     * fail the import. The shell import is still considered succeeded if the project was created.
     * This follows the existing audit pattern where audit is best-effort.
     *
     * @param tenantId      target tenant
     * @param importName    optional project name override
     * @param exportPackage parsed export package from zip
     * @return import execution result with project shell and asset mapping skeleton
     */
    @Transactional
    public ImportExecuteResult executeShellImport(String tenantId, String importName,
                                                    ProjectExportPackageDto exportPackage) {
        assertTenantAccess(tenantId);

        String importId = Ids.newId("imp");
        String sourceProjectId = exportPackage.project() != null ? exportPackage.project().projectId() : null;
        String projectId = null;

        try {
            // Validate schema version
            validateSchemaVersion(exportPackage);

            // Create project shell
            String projectName = determineProjectName(importName, exportPackage);
            String projectDesc = exportPackage.project() != null ? exportPackage.project().description() : "";
            CreateProjectRequest createReq = new CreateProjectRequest(projectName, safeDescription(projectDesc));
            ProjectResponse created = tenantProjectService.createProject(tenantId, createReq);
            projectId = created.id();

            log.info("Created project shell: tenant={} projectId={} name={}", tenantId, projectId, projectName);

            // Build asset mapping skeleton (all assets need upload)
            List<ImportPreviewIssueDto> warnings = new ArrayList<>();
            List<ProjectImportAssetMappingDto> assetMappings = new ArrayList<>();
            int totalAssets = 0;

            List<ProjectExportAssetDto> assets = exportPackage.assets() != null
                    && exportPackage.assets().assets() != null
                    ? exportPackage.assets().assets() : List.of();
            totalAssets = assets.size();

            for (ProjectExportAssetDto asset : assets) {
                assetMappings.add(new ProjectImportAssetMappingDto(
                        asset.assetId(),
                        null,  // no target asset ID yet
                        "needs_upload"
                ));

                // Warn about missing checksum
                if (asset.checksum() == null || asset.checksum().isBlank()) {
                    warnings.add(new ImportPreviewIssueDto("MISSING_CHECKSUM", "warning",
                            "Asset '" + asset.assetId() + "' has no checksum — cannot verify integrity",
                            null));
                }
            }

            // Analyze effects for unsupported warnings
            if (exportPackage.render() != null && exportPackage.render().renderPlan() != null) {
                List<Map<String, Object>> ops = (List<Map<String, Object>>) exportPackage.render()
                        .renderPlan().getOrDefault("operations", List.of());
                Set<String> knownEffects = getKnownEffectKeys();
                for (Map<String, Object> op : ops) {
                    String effectKey = (String) op.get("effectKey");
                    if (effectKey != null && !knownEffects.contains(effectKey)) {
                        warnings.add(new ImportPreviewIssueDto("UNSUPPORTED_EFFECT", "warning",
                                "Effect '" + effectKey + "' is not in the known taxonomy",
                                "This effect may not be available in the target environment."));
                    }
                }
            }

            // Build asset summary
            ProjectImportAssetSummaryDto assetSummary = new ProjectImportAssetSummaryDto(
                    totalAssets, 0, totalAssets, 0, 0);

            // Persist metadata (within same transaction)
            boolean timelinePersisted = false;
            boolean renderPlanPersisted = false;
            boolean spatialPlanPersisted = false;
            boolean effectMetadataPersisted = false;
            String timelineRevisionId = null;

            try {
                // Scrub and save timeline JSON
                String timelineJson = metadataScrubber.scrub(
                        exportPackage.timeline() != null ? toJson(exportPackage.timeline()) : null);
                if (timelineJson != null) {
                    timelinePersisted = true;
                }

                // Scrub and save render plan JSON
                String renderPlanJson = metadataScrubber.scrub(
                        exportPackage.render() != null && exportPackage.render().renderPlan() != null
                                ? exportPackage.render().renderPlan().toString() : null);
                if (renderPlanJson != null) {
                    renderPlanPersisted = true;
                }

                // Scrub and save spatial plan JSON
                String spatialPlanJson = metadataScrubber.scrub(
                        exportPackage.render() != null && exportPackage.render().spatialPlan() != null
                                ? exportPackage.render().spatialPlan().toString() : null);
                if (spatialPlanJson != null) {
                    spatialPlanPersisted = true;
                }

                // Scrub and save effect metadata (if available in zip)
                String effectTaxonomyJson = null;
                String appliedEffectsJson = null;
                // Note: effect taxonomy and applied effects are not currently parsed by ProjectExportZipReader
                // They would require additional parsing logic to extract from effects/effect-taxonomy.json
                // and effects/applied-effects.json entries
                if (effectTaxonomyJson != null || appliedEffectsJson != null) {
                    effectMetadataPersisted = true;
                }

                // Build asset mapping JSON
                String assetMappingJson = buildAssetMappingJson(assetMappings);

                // Save import metadata record
                ProjectImportMetadataRepository.MetadataRecord metadataRecord =
                        new ProjectImportMetadataRepository.MetadataRecord(
                                Ids.newId("imp-meta"),
                                tenantId,
                                projectId,
                                importId,
                                sourceProjectId,
                                exportPackage.manifest() != null ? exportPackage.manifest().exportId() : null,
                                exportPackage.schemaVersion(),
                                timelineJson,
                                null, // timelineOtioJson - not implemented yet
                                renderPlanJson,
                                spatialPlanJson,
                                null, // exportProfilesJson - not implemented yet
                                effectTaxonomyJson,
                                appliedEffectsJson,
                                assetMappingJson,
                                java.time.Instant.now()
                        );
                metadataRepository.save(metadataRecord);

                log.info("Persisted import metadata: importId={} timeline={} render={} spatial={} effects={}",
                        importId, timelinePersisted, renderPlanPersisted, spatialPlanPersisted, effectMetadataPersisted);

            } catch (Exception metadataEx) {
                log.error("Failed to persist import metadata, rolling back", metadataEx);
                throw new RuntimeException("Metadata persistence failed", metadataEx);
            }

            // Record audit (best-effort: failure does not fail the import)
            try {
                recordImportAudit(importId, tenantId, projectId, sourceProjectId,
                        exportPackage.schemaVersion(), totalAssets, warnings.size(),
                        timelinePersisted, renderPlanPersisted, spatialPlanPersisted, effectMetadataPersisted);
            } catch (Exception auditEx) {
                log.warn("Audit recording failed but import succeeded: {}", auditEx.getMessage());
            }

            return new ImportExecuteResult(
                    importId, projectId, MODE_SHELL_ONLY,
                    assetSummary, assetMappings, warnings,
                    new ImportMetadataSummaryDto(timelinePersisted, renderPlanPersisted,
                            spatialPlanPersisted, effectMetadataPersisted)
            );

        } catch (Exception e) {
            // Rollback: delete the project shell if it was created
            // Note: @Transactional will also rollback, but we explicitly delete to handle
            // cases where the transaction boundary might be different
            if (projectId != null) {
                log.error("Import failed, rolling back project shell: projectId={}", projectId, e);
                try {
                    projectRepository.deleteById(projectId);
                    log.info("Successfully rolled back project shell: projectId={}", projectId);
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback project shell: projectId={}", projectId, rollbackEx);
                }
            }
            throw e;
        }
    }

    private String determineProjectName(String importName, ProjectExportPackageDto pkg) {
        if (importName != null && !importName.isBlank()) {
            return importName;
        }
        if (pkg.project() != null && pkg.project().name() != null && !pkg.project().name().isBlank()) {
            return pkg.project().name() + " (Imported)";
        }
        return "Imported Project";
    }

    private String safeDescription(String desc) {
        if (desc == null) return "";
        // Truncate to 500 chars, strip any URLs
        String safe = desc.replaceAll("https?://[^\\s]+", "[url]");
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }

    private void validateSchemaVersion(ProjectExportPackageDto pkg) {
        String v = pkg.schemaVersion();
        if (v == null) {
            throw new IllegalArgumentException("Export package is missing schemaVersion.");
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(v)) {
            throw new IllegalArgumentException(
                    "Unsupported schema version: " + v + ". Expected: " + SUPPORTED_SCHEMA_VERSION);
        }
    }

    private Set<String> getKnownEffectKeys() {
        return Set.of(
                "video.fade_in", "video.fade_out", "video.cross_dissolve",
                "video.dissolve", "video.wipe", "video.slide", "video.zoom",
                "video.blur", "video.sharpen", "video.vignette",
                "video.natron_vignette", "video.chromatic",
                "video.natron_color_grade", "video.brightness", "video.contrast",
                "video.grayscale", "video.sepia", "video.particle_overlay",
                "video.overlay", "video.pip", "video.watermark",
                "text.subtitle_burn_in", "text.overlay",
                "audio.volume",
                "video.dash_drm", "video.shotstack_template",
                "video.remotion_template", "video.blender_scene"
        );
    }

    private String buildAssetMappingJson(List<ProjectImportAssetMappingDto> assetMappings) {
        try {
            Map<String, String> mapping = new LinkedHashMap<>();
            for (ProjectImportAssetMappingDto m : assetMappings) {
                mapping.put(m.sourceAssetId(), m.status());
            }
            return MAPPER.writeValueAsString(mapping);
        } catch (Exception e) {
            log.warn("Failed to build asset mapping JSON", e);
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }

    private void recordImportAudit(String importId, String tenantId, String projectId,
                                    String sourceProjectId, String schemaVersion,
                                    int assetCount, int warningsCount,
                                    boolean timelinePersisted, boolean renderPlanPersisted,
                                    boolean spatialPlanPersisted, boolean effectMetadataPersisted) {
        if (auditPort == null) {
            return;
        }
        try {
            Map<String, Object> auditPayload = new LinkedHashMap<>();
            auditPayload.put("importId", importId);
            auditPayload.put("mode", MODE_SHELL_ONLY);
            auditPayload.put("tenantId", tenantId);
            auditPayload.put("sourceProjectId", sourceProjectId != null ? sourceProjectId : "");
            auditPayload.put("schemaVersion", schemaVersion != null ? schemaVersion : "");
            auditPayload.put("assetCount", assetCount);
            auditPayload.put("needsUploadCount", assetCount);
            auditPayload.put("warningsCount", warningsCount);
            auditPayload.put("status", "SUCCEEDED");
            auditPayload.put("rollbackAttempted", false);
            auditPayload.put("rollbackSucceeded", false);

            // Explicit redaction verification: ensure no sensitive fields are present
            verifyAuditRedaction(auditPayload);

            auditPort.record("TENANT", "PROJECT_IMPORT_SHELL", "PROJECT_IMPORT",
                    "project", projectId, auditPayload);
        } catch (Exception e) {
            log.warn("Failed to record import shell audit: {}", e.getMessage());
        }
    }

    /**
     * Verifies that audit payload does not contain sensitive information.
     * Throws IllegalStateException if any forbidden key is found.
     */
    private void verifyAuditRedaction(Map<String, Object> auditPayload) {
        Set<String> forbiddenKeys = Set.of(
                "downloadUrl", "signedUrl", "storageUri", "storageRef",
                "bucket", "key", "zipBytes", "signedUrl", "url"
        );
        for (String key : auditPayload.keySet()) {
            String lowerKey = key.toLowerCase();
            for (String forbidden : forbiddenKeys) {
                if (lowerKey.contains(forbidden.toLowerCase())) {
                    throw new IllegalStateException(
                            "Audit payload contains forbidden key: " + key);
                }
            }
        }
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant: " + tenantId);
        }
    }

    /**
     * Asset mapping DTO for import response.
     */
    public record ImportPreviewAssetMappingDto(
            String sourceAssetId,
            String targetAssetId,
            String status,
            String filename,
            String type,
            Long sizeBytes
    ) {}
}
