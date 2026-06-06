package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.ProjectExportZipReader;
import com.example.platform.identity.app.ProjectImportPreviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST API for project import preview operations.
 *
 * <p>Validates export packages for import compatibility without creating
 * any persistent state.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /tenants/{tenantId}/project-imports/preview} — JSON import preview</li>
 *   <li>{@code POST /tenants/{tenantId}/project-imports/preview/archive} — zip import preview</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportPreviewController {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportPreviewController.class);
    private static final long MAX_ZIP_SIZE = 50L * 1024 * 1024; // 50 MB

    private final ProjectImportPreviewService previewService;
    private final ProjectExportZipReader zipReader;

    public ProjectImportPreviewController(ProjectImportPreviewService previewService,
                                           ProjectExportZipReader zipReader) {
        this.previewService = previewService;
        this.zipReader = zipReader;
    }

    /**
     * Preview a project import from a JSON export package.
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

    /**
     * Preview a project import from a zip archive.
     *
     * <p>Accepts a multipart/form-data upload of a project-export-v1.zip file.
     * Validates the zip structure, checksums, and entry safety before
     * delegating to the existing import preview logic.
     *
     * <p>Security:
     * <ul>
     *   <li>Zip bomb protection: 50 MB compressed, 200 MB uncompressed, 100 entries max</li>
     *   <li>Zip slip prevention: entry names validated against allowlist</li>
     *   <li>Checksum validation against sha256sums.txt</li>
     *   <li>No signed URLs downloaded</li>
     *   <li>No project created</li>
     * </ul>
     */
    @PostMapping(value = "/tenants/{tenantId}/project-imports/preview/archive",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewImportFromArchive(
            @PathVariable String tenantId,
            @RequestParam("file") MultipartFile file) {

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

            // Convert zip contents to import preview request
            ProjectExportPackageDto exportPackage = readResult.exportPackage();
            ProjectImportPreviewRequest previewRequest = new ProjectImportPreviewRequest(exportPackage);

            // Delegate to existing import preview service
            ProjectImportPreviewResponse response = previewService.previewImport(tenantId, previewRequest);

            // Merge zip validation warnings into preview response
            List<String> allWarnings = new java.util.ArrayList<>();
            allWarnings.addAll(readResult.warnings());
            if (response.warnings() != null) {
                for (ImportPreviewIssueDto w : response.warnings()) {
                    allWarnings.add(w.message());
                }
            }

            // Rebuild response with merged warnings if needed
            if (!readResult.warnings().isEmpty()) {
                List<ImportPreviewIssueDto> mergedWarnings = new java.util.ArrayList<>();
                for (String w : readResult.warnings()) {
                    mergedWarnings.add(new ImportPreviewIssueDto("ZIP_WARNING", "warning", w, null));
                }
                if (response.warnings() != null) {
                    mergedWarnings.addAll(response.warnings());
                }
                response = new ProjectImportPreviewResponse(
                        response.schemaVersion(), response.compatible(),
                        response.project(), response.assets(), response.effects(),
                        mergedWarnings, response.errors());
            }

            if (!response.compatible()) {
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);

        } catch (java.io.IOException e) {
            log.error("Failed to read zip archive", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "read_failed",
                    "message", "Failed to read zip archive."
            ));
        }
    }
}
