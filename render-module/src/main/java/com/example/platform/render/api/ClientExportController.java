package com.example.platform.render.api;

import com.example.platform.render.app.clientexport.ClientExportService;
import com.example.platform.render.app.clientexport.ClientExportService.ExportConfig;
import com.example.platform.render.domain.clientexport.ClientExportSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/render/client-exports")
public class ClientExportController {

    private final ClientExportService clientExportService;

    public ClientExportController(ClientExportService clientExportService) {
        this.clientExportService = clientExportService;
    }

    @PostMapping
    public ExportConfig startSession(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody StartClientExportRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        String tier = request.tier() != null ? request.tier() : "FREE";
        return clientExportService.createSessionWithConfig(
                effectiveTenant,
                request.workspaceId(),
                request.projectId(),
                request.userId(),
                tier,
                request.preset(),
                request.timelineSnapshotId());
    }

    @PostMapping("/{sessionId}/progress")
    public Map<String, Object> updateProgress(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId,
            @RequestBody ProgressUpdateRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        ClientExportSession session = clientExportService.updateProgress(
                sessionId, request.status(), request.progress());
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "progress", session.progress());
    }

    @PostMapping(value = "/{sessionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndComplete(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", required = false) Long durationSeconds,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "registerArtifact", defaultValue = "true") boolean registerArtifact)
            throws Exception {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        ClientExportSession session = clientExportService.uploadAndComplete(
                sessionId, file, durationSeconds, checksum, registerArtifact);
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "storageUri", session.outputUri() != null ? session.outputUri() : "",
                "artifactId", session.artifactId() != null ? session.artifactId() : "",
                "downloadUrl", session.downloadPath() != null ? session.downloadPath() : "");
    }

    @PostMapping("/{sessionId}/fail")
    public Map<String, Object> failSession(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId,
            @RequestBody FailRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        ClientExportSession session = clientExportService.failSession(
                sessionId, request.errorCode(), request.errorMessage());
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "errorCode", session.errorCode() != null ? session.errorCode() : "");
    }

    @PostMapping("/{sessionId}/cancel")
    public Map<String, Object> cancelSession(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        ClientExportSession session = clientExportService.cancelSession(sessionId);
        return Map.of("sessionId", session.id(), "status", session.status());
    }

    @GetMapping("/{sessionId}")
    public ClientExportSession getSession(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        return clientExportService.findSessionForTenant(sessionId, effectiveTenant);
    }

    @GetMapping
    public List<ClientExportSession> listSessions(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        if (projectId != null && !projectId.isBlank()) {
            return clientExportService.listByTenantAndProject(effectiveTenant, projectId, limit, offset);
        }
        return clientExportService.listByTenant(effectiveTenant, limit, offset);
    }

    @GetMapping("/{sessionId}/download")
    public ResponseEntity<Resource> download(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @PathVariable String sessionId) throws Exception {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        ClientExportSession session = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        Path file = clientExportService.resolveUploadPath(sessionId);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        String filename = "export-" + sessionId + "." + session.format();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("video/" + session.format()))
                .body(resource);
    }

    public record StartClientExportRequest(
            String projectId,
            String workspaceId,
            String userId,
            String tier,
            String preset,
            String timelineSnapshotId) {}

    public record ProgressUpdateRequest(String status, int progress) {}

    public record FailRequest(String errorCode, String errorMessage) {}
}
