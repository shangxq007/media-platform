package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectImportPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for project import preview operations.
 *
 * <p>Validates export packages for import compatibility without creating
 * any persistent state.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportPreviewController {

    private final ProjectImportPreviewService previewService;

    public ProjectImportPreviewController(ProjectImportPreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * Preview a project import from an export package.
     *
     * <p>Validates the export package structure, checks asset availability,
     * and reports compatibility issues. Does not create any project or
     * persist any data.
     *
     * @param tenantId target tenant for the import
     * @param request  import preview request containing the export package
     * @return preview response with compatibility analysis
     */
    @PostMapping("/tenants/{tenantId}/project-imports/preview")
    public ResponseEntity<ProjectImportPreviewResponse> previewImport(
            @PathVariable String tenantId,
            @RequestBody ProjectImportPreviewRequest request) {

        ProjectImportPreviewResponse response = previewService.previewImport(tenantId, request);

        if (!response.compatible()) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
