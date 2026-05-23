package com.example.platform.render.api;

import com.example.platform.render.app.clientexport.ClientExportService;
import com.example.platform.render.app.clientexport.ClientExportService.ClientExportSession;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public Map<String, Object> startSession(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestBody StartClientExportRequest request) {
        String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
        ClientExportSession session = clientExportService.startSession(
                effectiveTenant,
                request.projectId(),
                request.timelineSnapshotId(),
                request.preset());
        String uploadUrl = "/api/v1/render/client-exports/" + session.sessionId() + "/upload";
        return Map.of(
                "sessionId", session.sessionId(),
                "uploadUrl", uploadUrl,
                "status", session.status());
    }

    @PostMapping(value = "/{sessionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndComplete(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", required = false) Long durationSeconds,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "registerArtifact", defaultValue = "true") boolean registerArtifact)
            throws Exception {
        ClientExportSession session = clientExportService.uploadAndComplete(
                sessionId, file, durationSeconds, checksum, registerArtifact);
        return Map.of(
                "sessionId", session.sessionId(),
                "status", session.status(),
                "storageUri", session.storageUri() != null ? session.storageUri() : "",
                "artifactId", session.artifactId() != null ? session.artifactId() : "",
                "downloadUrl", session.downloadPath() != null ? session.downloadPath() : "");
    }

    @GetMapping("/{sessionId}")
    public ClientExportSession getSession(@PathVariable String sessionId) {
        return clientExportService.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session: " + sessionId));
    }

    @GetMapping("/{sessionId}/download")
    public ResponseEntity<Resource> download(@PathVariable String sessionId) throws Exception {
        Path file = clientExportService.resolveUploadPath(sessionId);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export-" + sessionId + ".mp4\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    public record StartClientExportRequest(
            String projectId,
            String timelineSnapshotId,
            String preset) {}
}
