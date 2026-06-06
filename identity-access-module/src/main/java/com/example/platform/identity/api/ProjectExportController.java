package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportService;
import com.example.platform.identity.app.ProjectExportZipPackagingService;
import com.example.platform.shared.web.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST API for project export operations.
 *
 * <p>Supports metadata-only and linked-assets export in v1. Other modes
 * (bundled_assets, render_reproduction) are planned for future releases.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /tenants/{tenantId}/projects/{projectId}/exports} — JSON export</li>
 *   <li>{@code POST /tenants/{tenantId}/projects/{projectId}/exports/archive} — zip download</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectExportController {

    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9._-]");

    /**
     * Sanitize a filename for Content-Disposition header.
     * Removes path traversal sequences and unsafe characters.
     */
    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "unknown";
        String safe = SAFE_FILENAME.matcher(name).replaceAll("_");
        safe = safe.replace("..", "_");
        safe = safe.replaceAll("^[._]+|[._]+$", "");
        return safe.isEmpty() ? "unknown" : safe;
    }

    private final ProjectExportService projectExportService;
    private final ProjectExportZipPackagingService zipPackagingService;

    public ProjectExportController(ProjectExportService projectExportService,
                                    ProjectExportZipPackagingService zipPackagingService) {
        this.projectExportService = projectExportService;
        this.zipPackagingService = zipPackagingService;
    }

    /**
     * Create a project export (JSON response).
     */
    @PostMapping("/tenants/{tenantId}/projects/{projectId}/exports")
    public ResponseEntity<?> createExport(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody(required = false) ProjectExportRequest request) {

        String mode = (request != null && request.mode() != null && !request.mode().isBlank())
                ? request.mode() : ProjectExportRequest.MODE_METADATA_ONLY;

        // Reject unsupported modes with 501
        if (!ProjectExportRequest.MODE_METADATA_ONLY.equals(mode)
                && !ProjectExportRequest.MODE_LINKED_ASSETS.equals(mode)) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "unsupported_export_mode",
                    "message", "Export mode '" + mode + "' is not implemented.",
                    "supportedModes", List.of(
                            ProjectExportRequest.MODE_METADATA_ONLY,
                            ProjectExportRequest.MODE_LINKED_ASSETS)
            ));
        }

        Integer ttlSeconds = (request != null) ? request.signedUrlTtlSeconds() : null;

        // Validate TTL for linked_assets
        if (ProjectExportRequest.MODE_LINKED_ASSETS.equals(mode) && ttlSeconds != null
                && ttlSeconds > 86400) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_ttl",
                    "message", "signedUrlTtlSeconds must be <= 86400 (24 hours). " +
                               "Requested: " + ttlSeconds,
                    "maxTtlSeconds", 86400
            ));
        }

        try {
            ProjectExportResponse response = projectExportService.createExport(
                    tenantId, projectId, new ProjectExportRequest(mode, ttlSeconds));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "project_not_found",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Create a project export archive (zip download).
     *
     * <p>Generates a zip file following the project-export-v1 directory structure.
     * Currently supports {@code metadata_only} and {@code linked_assets} modes.
     * {@code bundled_assets} is not yet implemented.
     *
     * <p>Request body is required. Mode must be explicitly specified.
     * Blank or missing mode returns 400 Bad Request.
     */
    @PostMapping("/tenants/{tenantId}/projects/{projectId}/exports/archive")
    public ResponseEntity<?> createExportArchive(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody ProjectExportRequest request) {

        // Require explicit mode
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_request",
                    "message", "Request body is required."
            ));
        }

        String mode = request.mode();
        if (mode == null || mode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_mode",
                    "message", "Export mode is required. Supported: metadata_only, linked_assets"
            ));
        }

        // Only metadata_only and linked_assets are supported for zip
        if (!ProjectExportRequest.MODE_METADATA_ONLY.equals(mode)
                && !ProjectExportRequest.MODE_LINKED_ASSETS.equals(mode)) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "unsupported_export_mode",
                    "message", "Export mode '" + mode + "' is not implemented for zip archive.",
                    "supportedModes", List.of(
                            ProjectExportRequest.MODE_METADATA_ONLY,
                            ProjectExportRequest.MODE_LINKED_ASSETS)
            ));
        }

        Integer ttlSeconds = request.signedUrlTtlSeconds();

        // Validate TTL for linked_assets
        if (ProjectExportRequest.MODE_LINKED_ASSETS.equals(mode) && ttlSeconds != null
                && ttlSeconds > 86400) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_ttl",
                    "message", "signedUrlTtlSeconds must be <= 86400 (24 hours). " +
                               "Requested: " + ttlSeconds,
                    "maxTtlSeconds", 86400
            ));
        }

        try {
            ProjectExportResponse exportResponse = projectExportService.createExport(
                    tenantId, projectId, new ProjectExportRequest(mode, ttlSeconds));

            byte[] zipBytes = zipPackagingService.packageMetadataOnly(exportResponse);

            // Sanitize filename: only alphanumeric, dots, dashes, underscores
            String safeProjectId = sanitizeFilename(projectId);
            String safeExportId = sanitizeFilename(exportResponse.exportId());
            String filename = "project-export-" + safeProjectId + "-" + safeExportId + ".zip";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(zipBytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(zipBytes);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "not_implemented",
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "project_not_found",
                    "message", e.getMessage()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "packaging_failed",
                    "message", "Failed to create export archive."
            ));
        }
    }

    /**
     * Get export status (placeholder for async export in future versions).
     */
    @GetMapping("/tenants/{tenantId}/projects/{projectId}/exports/{exportId}")
    public ResponseEntity<Void> getExportStatus() {
        return ResponseEntity.notFound().build();
    }
}
