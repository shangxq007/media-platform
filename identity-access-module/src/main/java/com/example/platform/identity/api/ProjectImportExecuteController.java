package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportZipReader;
import com.example.platform.identity.app.ProjectImportExecuteService;
import com.example.platform.identity.app.ProjectImportPreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * REST API for project import from zip archive operations.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code POST /tenants/{tenantId}/project-imports/archive} — import project shell from zip</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportExecuteController {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportExecuteController.class);
    private static final long MAX_ZIP_SIZE = 50L * 1024 * 1024; // 50 MB

    private final ProjectImportExecuteService importExecuteService;
    private final ProjectExportZipReader zipReader;

    public ProjectImportExecuteController(ProjectImportExecuteService importExecuteService,
                                           ProjectExportZipReader zipReader) {
        this.importExecuteService = importExecuteService;
        this.zipReader = zipReader;
    }

    /**
     * Import a project shell from a zip archive.
     *
     * <p>Accepts a multipart/form-data upload of a project-export-v1.zip file.
     * Creates a project shell with metadata but does NOT download signed URLs,
     * copy media, or register artifacts.
     *
     * <p>Transactional: the entire import operation is wrapped in a transaction.
     * If any step fails after project creation, the transaction is rolled back
     * and no project shell is left behind.
     * <ul>
     *   <li>Zip validation via ProjectExportZipReader</li>
     *   <li>Source tenantId ignored, uses path tenantId</li>
     *   <li>No signed URLs downloaded</li>
     *   <li>No storageUri written to response or audit</li>
     * </ul>
     */
    @PostMapping(value = "/tenants/{tenantId}/project-imports/archive",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importFromArchive(
            @PathVariable String tenantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "importName", required = false) String importName,
            @RequestParam(value = "mode", required = false, defaultValue = "shell_only") String mode) {

        // Validate mode
        if (!"shell_only".equals(mode)) {
            return ResponseEntity.status(501).body(Map.of(
                    "error", "unsupported_mode",
                    "message", "Only 'shell_only' mode is currently supported.",
                    "supportedModes", List.of("shell_only")
            ));
        }

        // Validate file upload
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_file",
                    "message", "A zip file is required."
            ));
        }

        // Check file size before reading
        if (file.getSize() > MAX_ZIP_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "file_too_large",
                    "message", "Zip file exceeds maximum size of " + MAX_ZIP_SIZE / (1024 * 1024) + " MB."
            ));
        }

        try {
            // Read and validate zip
            ProjectExportZipReader.ZipReadResult readResult =
                    zipReader.readArchive(file.getInputStream(), file.getSize());

            if (!readResult.valid()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_archive",
                        "message", "Zip archive validation failed.",
                        "errors", readResult.errors(),
                        "warnings", readResult.warnings()
                ));
            }

            // Execute shell import
            ProjectImportExecuteService.ImportExecuteResult result =
                    importExecuteService.executeShellImport(tenantId, importName, readResult.exportPackage());

            // Build response
            ProjectImportExecuteResponse response = buildResponse(result, readResult.warnings());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Failed to read zip archive", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "read_failed",
                    "message", "Failed to read zip archive."
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Import validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "validation_failed",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Import failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "import_failed",
                    "message", "An unexpected error occurred during import."
            ));
        }
    }

    private ProjectImportExecuteResponse buildResponse(ProjectImportExecuteService.ImportExecuteResult result,
                                                        List<String> zipWarnings) {
        // Merge zip warnings with import warnings
        List<String> allWarnings = new ArrayList<>(zipWarnings);
        if (result.warnings() != null) {
            for (ImportPreviewIssueDto w : result.warnings()) {
                allWarnings.add(w.message());
            }
        }

        return new ProjectImportExecuteResponse(
                result.importId(),
                "SUCCEEDED",
                result.targetProjectId(),
                result.mode(),
                result.assetSummary(),
                result.assetMappings(),
                allWarnings,
                result.metadata()
        );
    }
}
