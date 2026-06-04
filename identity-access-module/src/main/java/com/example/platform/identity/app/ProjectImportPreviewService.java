package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.security.SafeDownloadUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Service for previewing project imports from export packages.
 *
 * <p>Validates the export package structure, checks asset availability,
 * and reports compatibility issues without creating any persistent state.
 */
@Service
public class ProjectImportPreviewService {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportPreviewService.class);
    private static final String SUPPORTED_SCHEMA_VERSION = "project-export-v1";
    // Only modes that Import can actually process
    private static final Set<String> SUPPORTED_EXPORT_MODES = Set.of(
            "metadata_only", "linked_assets"
    );

    // Known effect keys from Effect Taxonomy v1
    private static final Set<String> KNOWN_EFFECT_KEYS = Set.of(
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

    private AuditPort auditPort;

    @Autowired(required = false)
    public void setAuditPort(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * Preview a project import from an export package.
     *
     * @param tenantId  target tenant for the import
     * @param request   import preview request containing the export package
     * @return preview response with compatibility analysis
     */
    public ProjectImportPreviewResponse previewImport(String tenantId,
                                                        ProjectImportPreviewRequest request) {
        List<ImportPreviewIssueDto> warnings = new ArrayList<>();
        List<ImportPreviewIssueDto> errors = new ArrayList<>();

        ProjectExportPackageDto exportPkg = request.exportPackage();

        // 1. Validate schema version
        if (exportPkg.schemaVersion() == null || !SUPPORTED_SCHEMA_VERSION.equals(exportPkg.schemaVersion())) {
            errors.add(new ImportPreviewIssueDto(
                    "UNSUPPORTED_SCHEMA_VERSION", "error",
                    "Unsupported export schema version: " + exportPkg.schemaVersion(),
                    "Expected: " + SUPPORTED_SCHEMA_VERSION));
            return buildIncompatibleResponse(exportPkg, warnings, errors, tenantId);
        }

        // 2. Validate export mode
        if (exportPkg.exportMode() == null || !SUPPORTED_EXPORT_MODES.contains(exportPkg.exportMode())) {
            errors.add(new ImportPreviewIssueDto(
                    "UNSUPPORTED_EXPORT_MODE", "error",
                    "Unsupported export mode: " + exportPkg.exportMode(),
                    "Supported: " + SUPPORTED_EXPORT_MODES));
        }

        // 3. Validate project metadata
        if (exportPkg.project() == null) {
            errors.add(new ImportPreviewIssueDto(
                    "MISSING_PROJECT", "error",
                    "Export package is missing project metadata", null));
        }

        // 4. Analyze assets
        ImportPreviewAssetSummaryDto assetSummary = analyzeAssets(exportPkg, warnings, errors);

        // 5. Analyze effects
        ImportPreviewEffectSummaryDto effectSummary = analyzeEffects(exportPkg, warnings);

        // 6. Validate spatial coordinates if present
        validateSpatialCoordinates(exportPkg, warnings, errors);

        boolean compatible = errors.isEmpty();

        // Record audit
        recordPreviewAudit(tenantId, exportPkg, compatible, assetSummary, effectSummary);

        return new ProjectImportPreviewResponse(
                "project-import-preview-v1",
                compatible,
                buildProjectPreview(exportPkg),
                assetSummary,
                effectSummary,
                warnings,
                errors
        );
    }

    private ImportPreviewAssetSummaryDto analyzeAssets(ProjectExportPackageDto exportPkg,
                                                         List<ImportPreviewIssueDto> warnings,
                                                         List<ImportPreviewIssueDto> errors) {
        if (exportPkg.assets() == null) {
            return new ImportPreviewAssetSummaryDto(0, 0, 0, 0);
        }

        List<ProjectExportAssetDto> assets = exportPkg.assets().assets();
        if (assets == null || assets.isEmpty()) {
            return new ImportPreviewAssetSummaryDto(0, 0, 0, 0);
        }

        int total = assets.size();
        int available = 0;
        int needsUpload = 0;

        for (ProjectExportAssetDto asset : assets) {
            if (asset.downloadUrl() != null && !asset.downloadUrl().isBlank()) {
                // Validate URL safety
                String urlError = SafeDownloadUrlValidator.validate(asset.downloadUrl());
                if (urlError != null) {
                    errors.add(new ImportPreviewIssueDto(
                            "UNSAFE_DOWNLOAD_URL", "error",
                            "Asset '" + asset.assetId() + "' has unsafe download URL",
                            urlError));
                    continue;
                }

                // Check if URL appears to be expired (basic heuristic)
                if (isUrlLikelyExpired(asset.downloadUrl())) {
                    warnings.add(new ImportPreviewIssueDto(
                            "EXPIRED_SIGNED_URL", "warning",
                            "Asset '" + asset.assetId() + "' has an expired or soon-to-expire signed URL",
                            "The signed URL will need to be regenerated during import."));
                    needsUpload++;
                } else {
                    available++;
                }

                // Validate checksum format if provided
                if (asset.checksum() != null && !com.example.platform.shared.io.ChecksumFormat.isValid(asset.checksum())) {
                    errors.add(new ImportPreviewIssueDto(
                            "INVALID_CHECKSUM_FORMAT", "error",
                            "Asset '" + asset.assetId() + "' has invalid checksum format",
                            "Expected sha256:<64 hex>, got: " + asset.checksum()));
                }

                // Warn if checksum is missing
                if (asset.checksum() == null || asset.checksum().isBlank()) {
                    warnings.add(new ImportPreviewIssueDto(
                            "MISSING_CHECKSUM", "warning",
                            "Asset '" + asset.assetId() + "' has no checksum",
                            "Checksum will be computed during download but cannot be pre-validated."));
                }
            } else {
                needsUpload++;
            }
        }

        return new ImportPreviewAssetSummaryDto(total, available, needsUpload, 0);
    }

    private ImportPreviewEffectSummaryDto analyzeEffects(ProjectExportPackageDto exportPkg,
                                                           List<ImportPreviewIssueDto> warnings) {
        // Count known vs unknown effects from the render plan
        int total = 0;
        int supported = 0;
        int unsupported = 0;

        if (exportPkg.render() != null && exportPkg.render().renderPlan() != null) {
            Map<String, Object> renderPlan = exportPkg.render().renderPlan();
            Object operations = renderPlan.get("operations");
            if (operations instanceof List<?> ops) {
                for (Object op : ops) {
                    if (op instanceof Map<?, ?> opMap) {
                        String effectKey = (String) opMap.get("effectKey");
                        if (effectKey != null) {
                            total++;
                            if (KNOWN_EFFECT_KEYS.contains(effectKey)) {
                                supported++;
                            } else {
                                unsupported++;
                                warnings.add(new ImportPreviewIssueDto(
                                        "UNSUPPORTED_EFFECT", "warning",
                                        "Effect '" + effectKey + "' is not in the known taxonomy",
                                        "This effect may not be available in the target environment."));
                            }
                        }
                    }
                }
            }
        }

        return new ImportPreviewEffectSummaryDto(total, supported, unsupported);
    }

    private void validateSpatialCoordinates(ProjectExportPackageDto exportPkg,
                                             List<ImportPreviewIssueDto> warnings,
                                             List<ImportPreviewIssueDto> errors) {
        if (exportPkg.render() == null || exportPkg.render().spatialPlan() == null) {
            return;
        }

        Map<String, Object> spatialPlan = exportPkg.render().spatialPlan();
        Object operations = spatialPlan.get("operations");
        if (operations instanceof List<?> ops) {
            for (Object op : ops) {
                if (op instanceof Map<?, ?> opMap) {
                    // Validate ppm coordinates are integers and in valid range
                    for (String coordKey : new String[]{"x", "y", "width", "height"}) {
                        Object val = opMap.get(coordKey);
                        if (val != null) {
                            if (val instanceof Number num) {
                                double d = num.doubleValue();
                                if (d < 0 || d > 1_000_000) {
                                    errors.add(new ImportPreviewIssueDto(
                                            "INVALID_SPATIAL_COORDINATE", "error",
                                            "Spatial coordinate '" + coordKey + "' out of range: " + d,
                                            "normalized_ppm values must be between 0 and 1,000,000."));
                                }
                                if (d != Math.floor(d)) {
                                    warnings.add(new ImportPreviewIssueDto(
                                            "NON_INTEGER_PPM", "warning",
                                            "Spatial coordinate '" + coordKey + "' is not an integer: " + d,
                                            "normalized_ppm should use integer values per spec."));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isUrlLikelyExpired(String url) {
        // Basic heuristic: check for common expiration indicators
        // In production, this would parse the URL and check the expiration timestamp
        if (url.contains("Expires=")) {
            try {
                int idx = url.indexOf("Expires=");
                String expiresStr = url.substring(idx + 8);
                int endIdx = expiresStr.indexOf('&');
                if (endIdx > 0) expiresStr = expiresStr.substring(0, endIdx);
                long expires = Long.parseLong(expiresStr);
                return expires < Instant.now().getEpochSecond();
            } catch (Exception e) {
                // If we can't parse, assume it might be expired
                return true;
            }
        }
        // If no expiration info, assume it might be expired (conservative)
        return false;
    }

    private ImportPreviewProjectDto buildProjectPreview(ProjectExportPackageDto exportPkg) {
        if (exportPkg.project() == null) {
            return new ImportPreviewProjectDto(null, null, null);
        }
        return new ImportPreviewProjectDto(
                exportPkg.project().projectId(),
                exportPkg.project().name(),
                exportPkg.project().description()
        );
    }

    private ProjectImportPreviewResponse buildIncompatibleResponse(ProjectExportPackageDto exportPkg,
                                                                     List<ImportPreviewIssueDto> warnings,
                                                                     List<ImportPreviewIssueDto> errors,
                                                                     String tenantId) {
        return new ProjectImportPreviewResponse(
                "project-import-preview-v1",
                false,
                buildProjectPreview(exportPkg),
                new ImportPreviewAssetSummaryDto(0, 0, 0, 0),
                new ImportPreviewEffectSummaryDto(0, 0, 0),
                warnings,
                errors
        );
    }

    private void recordPreviewAudit(String tenantId, ProjectExportPackageDto exportPkg,
                                     boolean compatible, ImportPreviewAssetSummaryDto assetSummary,
                                     ImportPreviewEffectSummaryDto effectSummary) {
        try {
            if (auditPort != null) {
                auditPort.record("TENANT", "PROJECT_IMPORT_PREVIEW", "PROJECT_IMPORT",
                        "project", exportPkg.project() != null ? exportPkg.project().projectId() : "unknown",
                        Map.of("compatible", compatible,
                                "exportMode", exportPkg.exportMode() != null ? exportPkg.exportMode() : "unknown",
                                "assetCount", assetSummary.total(),
                                "supportedEffects", effectSummary.supported(),
                                "unsupportedEffects", effectSummary.unsupported(),
                                "tenantId", tenantId));
            }
        } catch (Exception e) {
            log.warn("Failed to record import preview audit: {}", e.getMessage());
        }
    }
}
