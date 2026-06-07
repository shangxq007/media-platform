package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportController {

    private final ProjectImportService importService;

    public ProjectImportController(ProjectImportService importService) {
        this.importService = importService;
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
                    "error", "unsupported_mode",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "import_failed",
                    "message", "An unexpected error occurred during import."
            ));
        }
    }
}
