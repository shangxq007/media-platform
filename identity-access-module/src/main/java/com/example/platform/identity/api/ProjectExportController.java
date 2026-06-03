package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportService;
import com.example.platform.shared.web.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for project export operations.
 *
 * <p>Supports metadata-only export in v1. Other modes (linked_assets, bundled_assets,
 * render_reproduction) are planned for future releases.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectExportController {

    private final ProjectExportService projectExportService;

    public ProjectExportController(ProjectExportService projectExportService) {
        this.projectExportService = projectExportService;
    }

    /**
     * Create a metadata-only export for a project.
     *
     * <p>Only {@code mode=metadata_only} is supported in v1. Other modes will return
     * {@code 501 Not Implemented}.
     *
     * @param tenantId  tenant identifier
     * @param projectId project identifier
     * @param request   export request containing the export mode
     * @return export response containing all project metadata
     */
    @PostMapping("/tenants/{tenantId}/projects/{projectId}/exports")
    public ResponseEntity<?> createExport(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody(required = false) ProjectExportRequest request) {

        // Default to metadata_only if not specified
        String mode = (request != null && request.mode() != null && !request.mode().isBlank())
                ? request.mode() : ProjectExportRequest.MODE_METADATA_ONLY;
        Integer ttlSeconds = (request != null) ? request.signedUrlTtlSeconds() : null;

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
     * Get export status (placeholder for async export in future versions).
     * <p>In v1, exports are synchronous. This endpoint returns 404.
     */
    @GetMapping("/tenants/{tenantId}/projects/{projectId}/exports/{exportId}")
    public ResponseEntity<Void> getExportStatus() {
        // Placeholder for future async export support
        return ResponseEntity.notFound().build();
    }
}
