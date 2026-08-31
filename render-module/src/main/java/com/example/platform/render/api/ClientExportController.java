package com.example.platform.render.api;

import com.example.platform.render.app.clientexport.ClientExportService;
import com.example.platform.render.app.clientexport.ClientExportService.ExportConfig;
import com.example.platform.render.domain.clientexport.ClientExportSession;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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

    public ClientExportController(ClientExportService clientExportService) {
        this.clientExportService = clientExportService;
    }

    @PostMapping
    public ExportConfig startSession(
            @RequestBody StartClientExportRequest request) {
        throw FailClosedAuthorization.unavailable("client export session creation");
    }

    @PostMapping("/{sessionId}/progress")
    public Map<String, Object> updateProgress(
            @PathVariable String sessionId,
            @RequestBody ProgressUpdateRequest request) {
        throw FailClosedAuthorization.unavailable("client export progress mutation");
    }

    @PostMapping(value = "/{sessionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAndComplete(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", required = false) Long durationSeconds,
            @RequestParam(value = "checksum", required = false) String checksum,
            @RequestParam(value = "registerArtifact", defaultValue = "true") boolean registerArtifact)
            throws Exception {
        throw FailClosedAuthorization.unavailable("client export upload completion");
    }

    @PostMapping("/{sessionId}/fail")
    public Map<String, Object> failSession(
            @PathVariable String sessionId,
            @RequestBody FailRequest request) {
        throw FailClosedAuthorization.unavailable("client export failure mutation");
    }

    @PostMapping("/{sessionId}/cancel")
    public Map<String, Object> cancelSession(
            @PathVariable String sessionId) {
        throw FailClosedAuthorization.unavailable("client export cancellation");
    }

    @GetMapping("/{sessionId}")
    public ClientExportSession getSession(
            @PathVariable String sessionId) {
        throw FailClosedAuthorization.unavailable("client export session read");
    }

    @GetMapping
    public List<ClientExportSession> listSessions(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        throw FailClosedAuthorization.unavailable("client export session listing");
    }

    @GetMapping("/{sessionId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String sessionId) throws Exception {
        throw FailClosedAuthorization.unavailable("client export local file download");
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
