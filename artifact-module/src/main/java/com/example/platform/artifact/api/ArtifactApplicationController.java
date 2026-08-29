package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactAccess;
import com.example.platform.artifact.app.ArtifactApplicationService;
import com.example.platform.artifact.app.ArtifactApplicationService.ArtifactAccessException;
import com.example.platform.artifact.app.ArtifactScope;
import com.example.platform.artifact.app.ArtifactSummary;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter over the canonical Artifact application boundary. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs/{renderJobId}/artifacts")
public class ArtifactApplicationController {

    private final ArtifactApplicationService service;

    public ArtifactApplicationController(ArtifactApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ArtifactListResponse list(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String renderJobId,
            @RequestParam(defaultValue = "100") int limit) {
        List<ArtifactSummaryResponse> items = service.listArtifacts(
                new ArtifactScope(tenantId, projectId, renderJobId), limit).stream()
                .map(ArtifactApplicationController::toResponse)
                .toList();
        return new ArtifactListResponse(items, service.countArtifacts(
                new ArtifactScope(tenantId, projectId, renderJobId)));
    }

    @GetMapping("/{artifactId}/access")
    public ResponseEntity<?> access(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String renderJobId,
            @PathVariable String artifactId) {
        try {
            ArtifactAccess access = service.requestAccess(
                    new ArtifactScope(tenantId, projectId, renderJobId), new ArtifactId(artifactId));
            return ResponseEntity.ok(new ArtifactAccessResponse(new AccessDescriptor(
                    access.artifactId().value(), access.accessUrl().toString(), access.expiresAt())));
        } catch (ArtifactAccessException exception) {
            HttpStatus status = switch (exception.failure()) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case NOT_AVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case UNSUPPORTED -> HttpStatus.NOT_IMPLEMENTED;
                case ACCESS_FAILED -> HttpStatus.BAD_GATEWAY;
            };
            return ResponseEntity.status(status).body(new ArtifactAccessError(
                    exception.failure().name(), exception.getMessage()));
        }
    }

    private static ArtifactSummaryResponse toResponse(ArtifactSummary summary) {
        return new ArtifactSummaryResponse(
                summary.artifactId().value(),
                summary.mediaType().name(),
                summary.artifactKind().name(),
                summary.contentDigest().toString(),
                summary.byteLength(),
                summary.state().name(),
                summary.integrityState().name(),
                summary.createdAt());
    }

    public record ArtifactSummaryResponse(
            String id,
            String type,
            String kind,
            String contentDigest,
            long byteLength,
            String state,
            String integrityState,
            java.time.Instant createdAt) {}

    public record ArtifactListResponse(List<ArtifactSummaryResponse> items, long total) {}

    public record ArtifactAccessResponse(AccessDescriptor access) {}

    public record AccessDescriptor(String artifactId, String accessUrl, java.time.Instant expiresAt) {}

    public record ArtifactAccessError(String status, String message) {}
}
