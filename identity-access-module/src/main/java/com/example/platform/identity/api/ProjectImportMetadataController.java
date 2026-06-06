package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.ProjectImportedMetadataDetailDto;
import com.example.platform.identity.api.dto.ProjectImportedMetadataSummaryDto;
import com.example.platform.identity.app.ProjectImportMetadataReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * REST API for reading imported project metadata.
 *
 * <p>Provides read-only access to persisted import metadata for preview purposes.
 * Does not modify runtime state or fetch any external resources.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ProjectImportMetadataController {

    private static final Logger log = LoggerFactory.getLogger(ProjectImportMetadataController.class);

    private final ProjectImportMetadataReadService readService;

    public ProjectImportMetadataController(ProjectImportMetadataReadService readService) {
        this.readService = readService;
    }

    /**
     * Get the latest imported metadata summary for a project.
     *
     * @param tenantId  target tenant
     * @param projectId target project
     * @return summary of latest import metadata, or 404 if none exists
     */
    @GetMapping("/tenants/{tenantId}/projects/{projectId}/import-metadata")
    public ResponseEntity<?> getLatestImportMetadata(
            @PathVariable String tenantId,
            @PathVariable String projectId) {

        Optional<ProjectImportedMetadataSummaryDto> summary =
                readService.findLatestByProject(tenantId, projectId);

        if (summary.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(summary.get());
    }

    /**
     * Get imported metadata by import ID.
     *
     * @param tenantId target tenant
     * @param importId import identifier
     * @return summary of import metadata, or 404 if not found
     */
    @GetMapping("/tenants/{tenantId}/project-imports/{importId}/metadata")
    public ResponseEntity<?> getImportMetadataById(
            @PathVariable String tenantId,
            @PathVariable String importId) {

        Optional<ProjectImportedMetadataSummaryDto> summary =
                readService.findByImportId(tenantId, importId);

        if (summary.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(summary.get());
    }

    /**
     * Get detailed imported metadata for a project.
     *
     * @param tenantId  target tenant
     * @param projectId target project
     * @return detail of latest import metadata, or 404 if none exists
     */
    @GetMapping("/tenants/{tenantId}/projects/{projectId}/import-metadata/detail")
    public ResponseEntity<?> getLatestImportMetadataDetail(
            @PathVariable String tenantId,
            @PathVariable String projectId) {

        Optional<ProjectImportedMetadataDetailDto> detail =
                readService.findLatestDetailByProject(tenantId, projectId);

        if (detail.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(detail.get());
    }

    /**
     * Get detailed imported metadata by import ID.
     *
     * @param tenantId target tenant
     * @param importId import identifier
     * @return detail of import metadata, or 404 if not found
     */
    @GetMapping("/tenants/{tenantId}/project-imports/{importId}/metadata/detail")
    public ResponseEntity<?> getImportMetadataDetailById(
            @PathVariable String tenantId,
            @PathVariable String importId) {

        Optional<ProjectImportedMetadataDetailDto> detail =
                readService.findDetailByImportId(tenantId, importId);

        if (detail.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(detail.get());
    }
}
