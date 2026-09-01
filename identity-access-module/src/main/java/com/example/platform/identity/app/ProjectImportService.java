package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.storage.contract.ChecksumFormat;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for executing full project imports from export packages.
 */
@Service
public class ProjectImportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportService.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "project-export-v1";
    private static final Set<String> SUPPORTED_IMPORT_MODES = Set.of("metadata_only", "linked_assets");

    public static final String REASON_CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    public static final String REASON_UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    private final TenantProjectService tenantProjectService;
    private final AuditPort auditPort;

    public ProjectImportService(TenantProjectService tenantProjectService,
                                 @Autowired(required = false) AuditPort auditPort) {
        this.tenantProjectService = tenantProjectService;
        this.auditPort = auditPort;
    }

    public ProjectImportResponse executeImport(String tenantId, ProjectImportRequest request) {
        assertTenantAccess(tenantId);

        ProjectExportPackageDto payload = request.payload();
        List<ImportPreviewIssueDto> warnings = new ArrayList<>();
        String importId = Ids.newId("imp");

        try {
            // 1. Validate schema version
            validateSchemaVersion(payload);

            // 2. Validate export mode
            String exportMode = payload.exportMode();
            if (exportMode == null || !SUPPORTED_IMPORT_MODES.contains(exportMode)) {
                throw new IllegalArgumentException("Unsupported export mode for import: " + exportMode);
            }

            // 3. Determine import policy
            String assetPolicy = request.assetImportPolicy() != null
                    ? request.assetImportPolicy() : ProjectImportRequest.POLICY_METADATA_ONLY;
            if (ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER.equals(assetPolicy)) {
                throw new UnsupportedOperationException(
                        "download_and_register is unavailable until BlobStorage placement is materialized "
                                + "through Storage-owned canonical issuance and the Artifact owner write boundary");
            }

            // 4. Create or resolve target project
            String projectId = resolveTargetProject(tenantId, request, payload);

            // 5. Process assets based on policy
            Map<String, String> assetMappings = new LinkedHashMap<>();
            int imported = 0, rebound = 0, skipped = 0;

            if (ProjectImportRequest.POLICY_REQUIRE_EXISTING_MAPPING.equals(assetPolicy)) {
                var result = processRequireExistingMapping(tenantId, projectId, request, payload, warnings);
                assetMappings = result.mappings;
                rebound = result.rebound;
                skipped = result.skipped;
            } else {
                log.info("Import metadata_only for tenant={} project={}", tenantId, projectId);
            }

            // 6. Record success audit
            recordImportAudit(importId, tenantId, projectId, exportMode,
                    payload.project() != null ? payload.project().projectId() : null,
                    payload.schemaVersion(),
                    countAssets(payload), imported, rebound, skipped, warnings.size(),
                    assetPolicy, STATUS_SUCCEEDED, null, false, true);

            return new ProjectImportResponse(
                    importId, tenantId, projectId, exportMode,
                    assetMappings,
                    new ProjectImportAssetResultDto(imported, rebound, skipped),
                    warnings
            );

        } catch (Exception e) {
            String reasonCode = classifyFailureReason(e);
            boolean rollbackAttempted = false;
            log.warn("Import {} failed: reason={} message={}", importId, reasonCode, e.getMessage());

            // Record failure audit (no sensitive data)
            recordImportAudit(importId, tenantId, null,
                    payload.exportMode(),
                    payload.project() != null ? payload.project().projectId() : null,
                    payload.schemaVersion(),
                    countAssets(payload), 0, 0, 0, 0,
                    request.assetImportPolicy(), STATUS_FAILED, reasonCode,
                    rollbackAttempted, rollbackAttempted);

            // Re-throw with safe message
            throw e;
        }
    }

    private String classifyFailureReason(Exception e) {
        if (e instanceof ImportFailureException ife) {
            return ife.reasonCode();
        }
        if (e instanceof IllegalArgumentException) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("checksum")) return REASON_CHECKSUM_MISMATCH;
        }
        return REASON_UNEXPECTED_ERROR;
    }

    private void validateSchemaVersion(ProjectExportPackageDto payload) {
        String schemaVersion = payload.schemaVersion();
        if (schemaVersion == null) {
            throw new IllegalArgumentException(
                    "Export package is missing schemaVersion. Cannot import legacy/unknown format.");
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported schema version: " + schemaVersion + ". Expected: " + SUPPORTED_SCHEMA_VERSION);
        }
    }

    private String resolveTargetProject(String tenantId, ProjectImportRequest request,
                                         ProjectExportPackageDto payload) {
        if (request.createNewProject() != null && request.createNewProject()) {
            String projectName = request.projectNameOverride() != null
                    ? request.projectNameOverride()
                    : (payload.project() != null ? payload.project().name() + " (imported)" : "Imported Project");
            String projectDesc = payload.project() != null ? payload.project().description() : "";
            CreateProjectRequest createReq = new CreateProjectRequest(projectName, projectDesc);
            ProjectResponse created = tenantProjectService.createProject(tenantId, createReq);
            log.info("Created new project {} for import in tenant {}", created.id(), tenantId);
            return created.id();
        }

        String targetProjectId = request.targetProjectId();
        if (targetProjectId == null || targetProjectId.isBlank()) {
            throw new IllegalArgumentException(
                    "Either createNewProject=true or targetProjectId must be specified.");
        }

        ProjectResponse target = tenantProjectService.getProject(targetProjectId);
        if (target == null) {
            throw new IllegalArgumentException("Target project not found: " + targetProjectId);
        }
        if (!tenantId.equals(target.tenantId())) {
            throw new IllegalArgumentException("Target project does not belong to tenant: " + tenantId);
        }

        return targetProjectId;
    }

    private RebindResult processRequireExistingMapping(String tenantId, String projectId,
                                                        ProjectImportRequest request,
                                                        ProjectExportPackageDto payload,
                                                        List<ImportPreviewIssueDto> warnings) {
        Map<String, String> mappings = new LinkedHashMap<>();
        int rebound = 0, skipped = 0;

        Map<String, String> providedMappings = request.assetMappings();
        if (providedMappings == null || providedMappings.isEmpty()) {
            throw new IllegalArgumentException(
                    "assetMappings is required for require_existing_mapping policy.");
        }

        List<ProjectExportAssetDto> assets = payload.assets() != null && payload.assets().assets() != null
                ? payload.assets().assets() : List.of();

        for (ProjectExportAssetDto asset : assets) {
            String sourceId = asset.assetId();
            String targetId = providedMappings.get(sourceId);

            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing mapping for source asset: " + sourceId);
            }

            if (Boolean.TRUE.equals(request.requireChecksum()) && asset.checksum() != null) {
                if (!ChecksumFormat.isValid(asset.checksum())) {
                    throw new IllegalArgumentException(
                            "Invalid checksum format for asset " + sourceId + ": " + asset.checksum());
                }
            }

            mappings.put(sourceId, targetId);
            rebound++;
        }

        return new RebindResult(mappings, rebound, 0);
    }

    private int countAssets(ProjectExportPackageDto payload) {
        if (payload.assets() == null || payload.assets().assets() == null) return 0;
        return payload.assets().assets().size();
    }

    private void recordImportAudit(String importId, String tenantId, String projectId,
                                    String mode, String sourceProjectId, String schemaVersion,
                                    int assetCount, int imported, int rebound, int skipped,
                                    int warningsCount, String assetPolicy,
                                    String status, String failureReasonCode,
                                    boolean rollbackAttempted, boolean rollbackSucceeded) {
        try {
            if (auditPort != null) {
                Map<String, Object> auditPayload = new LinkedHashMap<>();
                auditPayload.put("importId", importId);
                auditPayload.put("mode", mode);
                auditPayload.put("tenantId", tenantId);
                auditPayload.put("sourceProjectId", sourceProjectId != null ? sourceProjectId : "");
                auditPayload.put("schemaVersion", schemaVersion != null ? schemaVersion : "");
                auditPayload.put("assetCount", assetCount);
                auditPayload.put("imported", imported);
                auditPayload.put("rebound", rebound);
                auditPayload.put("skipped", skipped);
                auditPayload.put("warningsCount", warningsCount);
                auditPayload.put("assetImportPolicy", assetPolicy != null ? assetPolicy : "");
                auditPayload.put("status", status);
                auditPayload.put("rollbackAttempted", rollbackAttempted);
                auditPayload.put("rollbackSucceeded", rollbackSucceeded);
                if (failureReasonCode != null) {
                    auditPayload.put("failureReasonCode", failureReasonCode);
                }
                auditPort.record("TENANT", "PROJECT_IMPORT", "PROJECT_IMPORT",
                        "project", projectId != null ? projectId : "unknown", auditPayload);
            }
        } catch (Exception e) {
            log.warn("Failed to record import audit: {}", e.getMessage());
        }
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant: " + tenantId);
        }
    }

    private record RebindResult(Map<String, String> mappings, int rebound, int skipped) {}

    /**
     * Internal exception carrying a standardized reason code for failure classification.
     */
    static class ImportFailureException extends IllegalArgumentException {
        private final String reasonCode;

        ImportFailureException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }
}
