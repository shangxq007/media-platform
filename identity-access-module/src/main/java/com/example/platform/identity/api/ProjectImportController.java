package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectImportPreviewService;
import com.example.platform.identity.app.ProjectImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for project import operations.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportController {

    private final ProjectImportPreviewService previewService;
    private final ProjectImportService importService;

    public ProjectImportController(ProjectImportPreviewService previewService,
                                    ProjectImportService importService) {
        this.previewService = previewService;
        this.importService = importService;
    }

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

    @PostMapping("/tenants/{tenantId}/project-imports")
    public ResponseEntity<?> executeImport(
            @PathVariable String tenantId,
            @RequestBody ProjectImportRequest request) {
        try {
            ProjectImportResponse response = importService.executeImport(tenantId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "import_failed",
                    "message", e.getMessage()
            ));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "not_implemented",
                    "message", e.getMessage()
            ));
        }
    }
}
