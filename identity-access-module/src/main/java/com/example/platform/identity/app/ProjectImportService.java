package com.example.platform.identity.app;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.io.ChecksumFormat;
import com.example.platform.shared.imports.DownloadedAsset;
import com.example.platform.shared.imports.ImportAssetDownloader;
import com.example.platform.shared.imports.ImportCleanupTracker;
import com.example.platform.shared.security.SafeDownloadUrlValidator;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
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

    public static final String REASON_UNSAFE_URL = "UNSAFE_URL";
    public static final String REASON_MISSING_DOWNLOAD_URL = "MISSING_DOWNLOAD_URL";
    public static final String REASON_HTTP_DOWNLOAD_FAILED = "HTTP_DOWNLOAD_FAILED";
    public static final String REASON_DOWNLOAD_TOO_LARGE = "DOWNLOAD_TOO_LARGE";
    public static final String REASON_CHECKSUM_REQUIRED = "CHECKSUM_REQUIRED";
    public static final String REASON_CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    public static final String REASON_SIZE_MISMATCH = "SIZE_MISMATCH";
    public static final String REASON_ARTIFACT_REGISTER_FAILED = "ARTIFACT_REGISTER_FAILED";
    public static final String REASON_STORAGE_WRITE_FAILED = "STORAGE_WRITE_FAILED";
    public static final String REASON_STORAGE_DELETE_FAILED = "STORAGE_DELETE_FAILED";
    public static final String REASON_ROLLBACK_FAILED = "ROLLBACK_FAILED";
    public static final String REASON_UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    private final TenantProjectService tenantProjectService;
    private final ArtifactCatalogService artifactCatalogService;
    private AuditPort auditPort;
    private ImportAssetDownloader assetDownloader;
    private BlobStorage blobStorage;

    public ProjectImportService(TenantProjectService tenantProjectService,
                                 ArtifactCatalogService artifactCatalogService) {
        this.tenantProjectService = tenantProjectService;
        this.artifactCatalogService = artifactCatalogService;
    }

    @Autowired(required = false)
    public void setAuditPort(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    @Autowired(required = false)
    public void setAssetDownloader(ImportAssetDownloader assetDownloader) {
        this.assetDownloader = assetDownloader;
    }

    @Autowired(required = false)
    public void setBlobStorage(BlobStorage blobStorage) {
        this.blobStorage = blobStorage;
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
            } else if (ProjectImportRequest.POLICY_DOWNLOAD_AND_REGISTER.equals(assetPolicy)) {
                var result = processDownloadAndRegister(importId, tenantId, projectId, request, payload, warnings);
                assetMappings = result.mappings;
                imported = result.imported;
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
            boolean rollbackAttempted = reasonCode != REASON_UNEXPECTED_ERROR
                    && reasonCode != REASON_UNSAFE_URL
                    && reasonCode != REASON_MISSING_DOWNLOAD_URL;
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

    private DownloadRegisterResult processDownloadAndRegister(String importId, String tenantId,
                                                                String projectId,
                                                                ProjectImportRequest request,
                                                                ProjectExportPackageDto payload,
                                                                List<ImportPreviewIssueDto> warnings) {
        if (assetDownloader == null) {
            throw new UnsupportedOperationException(
                    "download_and_register requires a configured ImportAssetDownloader.");
        }
        if (blobStorage == null) {
            throw new ImportFailureException(REASON_STORAGE_WRITE_FAILED,
                    "download_and_register requires a configured BlobStorage.");
        }

        List<ProjectExportAssetDto> assets = payload.assets() != null && payload.assets().assets() != null
                ? payload.assets().assets() : List.of();

        Map<String, String> mappings = new LinkedHashMap<>();
        ImportCleanupTracker tracker = new ImportCleanupTracker();

        for (ProjectExportAssetDto asset : assets) {
            String sourceId = asset.assetId();
            String downloadUrl = asset.downloadUrl();
            String storageUri = null;

            try {
                // Validate download URL exists
                if (downloadUrl == null || downloadUrl.isBlank()) {
                    throw new ImportFailureException(REASON_MISSING_DOWNLOAD_URL,
                            "Asset " + sourceId + " has no downloadUrl for download_and_register policy.");
                }

                // SSRF validation
                String urlError = SafeDownloadUrlValidator.validate(downloadUrl);
                if (urlError != null) {
                    throw new ImportFailureException(REASON_UNSAFE_URL,
                            "Unsafe downloadUrl for asset " + sourceId + ": " + urlError);
                }

                // Validate checksum format if provided
                if (asset.checksum() != null && !ChecksumFormat.isValid(asset.checksum())) {
                    throw new ImportFailureException(REASON_CHECKSUM_MISMATCH,
                            "Invalid checksum format for asset " + sourceId + ": " + asset.checksum());
                }

                // Require checksum if requested
                if (Boolean.TRUE.equals(request.requireChecksum()) && asset.checksum() == null) {
                    throw new ImportFailureException(REASON_CHECKSUM_REQUIRED,
                            "Checksum is required for asset " + sourceId + " but not provided.");
                }

                // Download asset
                DownloadedAsset downloaded;
                try {
                    downloaded = assetDownloader.download(downloadUrl);
                } catch (com.example.platform.shared.imports.AssetDownloadException e) {
                    throw new ImportFailureException(REASON_HTTP_DOWNLOAD_FAILED,
                            "Download failed for asset " + sourceId + ": " + e.reasonCode());
                }
                tracker.trackTempFile(downloaded.tempFile());

                // Validate size if payload provides it
                if (asset.sizeBytes() != null && asset.sizeBytes() > 0
                        && downloaded.sizeBytes() != asset.sizeBytes()) {
                    throw new ImportFailureException(REASON_SIZE_MISMATCH,
                            "Size mismatch for asset " + sourceId
                            + ": expected " + asset.sizeBytes() + ", got " + downloaded.sizeBytes());
                }

                // Validate checksum if payload provides it
                if (asset.checksum() != null) {
                    String normalizedPayload = ChecksumFormat.normalizeSha256(asset.checksum());
                    if (!normalizedPayload.equals(downloaded.checksum())) {
                        throw new ImportFailureException(REASON_CHECKSUM_MISMATCH,
                                "Checksum mismatch for asset " + sourceId
                                + ": expected " + normalizedPayload + ", got " + downloaded.checksum());
                    }
                }

                // Determine format from mimeType or filename
                String format = inferFormat(asset);
                String resolution = asset.width() != null && asset.height() != null
                        ? asset.width() + "x" + asset.height() : null;
                long durationSeconds = asset.duration() != null ? asset.duration().longValue() : 0L;

                // Write to BlobStorage using streaming Path (no full file read into memory)
                String objectKey = buildStorageObjectKey(tenantId, projectId, importId, sourceId, asset.filename());
                StorageObjectRef storedRef;
                try {
                    var cmd = PutObjectCommand.fromPath("imports", objectKey,
                            downloaded.tempFile(), asset.mimeType());
                    storedRef = blobStorage.put(cmd);
                    storageUri = storedRef.toStorageUri();
                } catch (Exception e) {
                    throw new ImportFailureException(REASON_STORAGE_WRITE_FAILED,
                            "Failed to write asset " + sourceId + " to storage: " + e.getMessage());
                }
                tracker.trackStoredBlob(storageUri);

                // Register artifact with real storageUri
                Artifact registered;
                try {
                    registered = artifactCatalogService.registerArtifact(
                            "import:" + importId,
                            projectId,
                            storageUri,
                            format,
                            resolution,
                            durationSeconds,
                            downloaded.sizeBytes(),
                            downloaded.checksum()
                    );
                } catch (Exception e) {
                    throw new ImportFailureException(REASON_ARTIFACT_REGISTER_FAILED,
                            "Failed to register artifact for asset " + sourceId + ": " + e.getMessage());
                }

                tracker.trackRegisteredArtifact(registered.id());
                mappings.put(sourceId, registered.id());
                log.info("Downloaded and registered: source={} target={} size={} checksum={}",
                        sourceId, registered.id(), downloaded.sizeBytes(), downloaded.checksum());

            } catch (ImportFailureException e) {
                // Rollback all previously registered artifacts and blobs
                List<String> rollbackErrors = tracker.rollback(this::deleteBlob, this::rollbackArtifact);
                throw e;
            } catch (Exception e) {
                // Unexpected error — also rollback
                List<String> rollbackErrors = tracker.rollback(this::deleteBlob, this::rollbackArtifact);
                throw new ImportFailureException(REASON_UNEXPECTED_ERROR,
                        "Unexpected error for asset " + sourceId + ": " + e.getMessage());
            }
        }

        // All assets succeeded — commit (cleanup temp files only)
        tracker.commit();
        return new DownloadRegisterResult(mappings, assets.size());
    }

    /**
     * Builds a safe, deterministic storage object key for an imported asset.
     * Format: imports/{tenantId}/{projectId}/{importId}/{sourceAssetId}/{safeFilename}
     */
    private String buildStorageObjectKey(String tenantId, String projectId, String importId,
                                          String sourceAssetId, String filename) {
        String safeFilename = sanitizeFilename(filename, sourceAssetId);
        return "imports/" + tenantId + "/" + projectId + "/" + importId + "/" + sourceAssetId + "/" + safeFilename;
    }

    /**
     * Sanitizes a filename for safe use in a storage object key.
     */
    private String sanitizeFilename(String filename, String fallbackId) {
        if (filename == null || filename.isBlank()) {
            return "asset-" + fallbackId;
        }
        // Remove path separators and other unsafe characters
        String safe = filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        // Remove leading dots and dashes
        safe = safe.replaceAll("^[.\\-]+", "");
        // Limit length to 128 chars
        if (safe.length() > 128) {
            safe = safe.substring(0, 128);
        }
        if (safe.isBlank()) {
            return "asset-" + fallbackId;
        }
        return safe;
    }

    /**
     * Delete a blob from storage for rollback cleanup.
     */
    private void deleteBlob(String storageUri) {
        try {
            blobStorage.deleteStorageUri(storageUri);
            log.info("Deleted blob for rollback: {}", storageUri);
        } catch (Exception e) {
            log.warn("Failed to delete blob {}: {}", storageUri, e.getMessage());
            throw e;
        }
    }

    /**
     * Rollback a registered artifact by tombstoning it.
     */
    private void rollbackArtifact(String artifactId) {
        try {
            artifactCatalogService.updateStatus(
                    artifactId,
                    com.example.platform.artifact.domain.ArtifactStatus.TOMBSTONED);
            log.info("Tombstoned artifact for rollback: {}", artifactId);
        } catch (Exception e) {
            log.warn("Failed to tombstone artifact {}: {}", artifactId, e.getMessage());
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
            if (msg != null && msg.contains("size")) return REASON_SIZE_MISMATCH;
            if (msg != null && msg.contains("downloadUrl")) return REASON_MISSING_DOWNLOAD_URL;
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

    private String inferFormat(ProjectExportAssetDto asset) {
        if (asset.mimeType() != null) {
            return switch (asset.mimeType()) {
                case "video/mp4" -> "mp4";
                case "video/quicktime" -> "mov";
                case "video/webm" -> "webm";
                case "image/png" -> "png";
                case "image/jpeg" -> "jpg";
                case "audio/wav" -> "wav";
                case "audio/mpeg" -> "mp3";
                case "audio/aac" -> "aac";
                default -> "unknown";
            };
        }
        if (asset.filename() != null) {
            int dot = asset.filename().lastIndexOf('.');
            if (dot > 0) return asset.filename().substring(dot + 1).toLowerCase();
        }
        return "unknown";
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
    private record DownloadRegisterResult(Map<String, String> mappings, int imported) {}

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
