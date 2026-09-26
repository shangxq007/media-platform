package com.example.platform.render.api;

import com.example.platform.render.app.RenderSurfaceAuthorization;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/render/client-exports")
public class ClientExportController {

    private final ClientExportService clientExportService;
    private final RenderSurfaceAuthorization authorization;

    public ClientExportController(ClientExportService clientExportService,
            RenderSurfaceAuthorization authorization) {
        this.clientExportService = clientExportService;
        this.authorization = authorization;
    }

    @PostMapping
    public ExportConfig startSession(
            @RequestBody StartClientExportRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        String tier = request.tier() != null ? request.tier() : "FREE";
        // AUTH-UNPROTECTED-FIX-001: starting an export session is a project-scoped WRITE.
        authorization.requireProjectWrite(effectiveTenant, request.projectId());
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
            @PathVariable String sessionId,
            @RequestBody ProgressUpdateRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession existing = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectWrite(effectiveTenant, existing.projectId());
        ClientExportSession session = clientExportService.updateProgress(
                sessionId, request.status(), request.progress());
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "progress", session.progress());
    }

    @PostMapping(value = "/{sessionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndComplete(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "checksum", required = false) String checksum)
            throws Exception {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession existing = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectWrite(effectiveTenant, existing.projectId());
        ClientExportSession session = clientExportService.uploadAndComplete(
                sessionId, file, checksum);
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "storageUri", session.outputUri() != null ? session.outputUri() : "",
                "artifactId", session.artifactId() != null ? session.artifactId() : "",
                "downloadUrl", session.downloadPath() != null ? session.downloadPath() : "");
    }

    @PostMapping("/{sessionId}/fail")
    public Map<String, Object> failSession(
            @PathVariable String sessionId,
            @RequestBody FailRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession existing = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectWrite(effectiveTenant, existing.projectId());
        ClientExportSession session = clientExportService.failSession(
                sessionId, request.errorCode(), request.errorMessage());
        return Map.of(
                "sessionId", session.id(),
                "status", session.status(),
                "errorCode", session.errorCode() != null ? session.errorCode() : "");
    }

    @PostMapping("/{sessionId}/cancel")
    public Map<String, Object> cancelSession(
            @PathVariable String sessionId) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession existing = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectWrite(effectiveTenant, existing.projectId());
        ClientExportSession session = clientExportService.cancelSession(sessionId);
        return Map.of("sessionId", session.id(), "status", session.status());
    }

    @GetMapping("/{sessionId}")
    public ClientExportSession getSession(
            @PathVariable String sessionId) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession session = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectRead(effectiveTenant, session.projectId());
        return session;
    }

    @GetMapping
    public List<ClientExportSession> listSessions(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (projectId != null && !projectId.isBlank()) {
            authorization.requireProjectRead(effectiveTenant, projectId);
            return clientExportService.listByTenantAndProject(effectiveTenant, projectId, limit, offset);
        }
        authorization.requireTenantRead(effectiveTenant);
        return clientExportService.listByTenant(effectiveTenant, limit, offset);
    }

    @GetMapping("/{sessionId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String sessionId) throws Exception {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        ClientExportSession session = clientExportService.findSessionForTenant(sessionId, effectiveTenant);
        authorization.requireProjectRead(effectiveTenant, session.projectId());
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
