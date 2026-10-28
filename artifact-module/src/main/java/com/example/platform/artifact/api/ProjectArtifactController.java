package com.example.platform.artifact.api;

import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Project projectAuthorization and Artifact commit state protect every producing domain equally. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/artifacts")
public class ProjectArtifactController {
    private final ArtifactApplicationService artifacts;
    private final ArtifactContentRead contents;
    private final ArtifactProjectAuthorizationPort projectAuthorization;

    public ProjectArtifactController(ArtifactApplicationService artifacts, ArtifactContentRead contents,
            ArtifactProjectAuthorizationPort projectAuthorization) {
        this.artifacts = artifacts;
        this.contents = contents;
        this.projectAuthorization = projectAuthorization;
    }

    @GetMapping("/{artifactId}/access")
    public ResponseEntity<?> access(@PathVariable String tenantId, @PathVariable String projectId,
            @PathVariable String artifactId) {
        projectAuthorization.requireRead(tenantId, projectId);
        try {
            var access = artifacts.requestProjectAccess(new ArtifactProjectScope(tenantId, projectId), new ArtifactId(artifactId));
            return ResponseEntity.ok(new ArtifactApplicationController.ArtifactAccessResponse(
                    new ArtifactApplicationController.AccessDescriptor(access.artifactId().value(), access.accessUrl().toString(), access.expiresAt())));
        } catch (ArtifactApplicationService.ArtifactAccessException failure) {
            return unavailable(failure);
        }
    }

    @GetMapping("/{artifactId}/content")
    public ResponseEntity<?> content(@PathVariable String tenantId, @PathVariable String projectId,
            @PathVariable String artifactId) {
        projectAuthorization.requireRead(tenantId, projectId);
        try {
            var content = contents.readProjectArtifact(new ArtifactProjectScope(tenantId, projectId), new ArtifactId(artifactId));
            byte[] bytes = content.bytes();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
                    .contentLength(bytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.fileName()).build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .eTag('"' + content.digest().canonicalValue() + '"').body(bytes);
        } catch (ArtifactApplicationService.ArtifactAccessException failure) {
            return unavailable(failure);
        }
    }

    private static ResponseEntity<?> unavailable(ArtifactApplicationService.ArtifactAccessException failure) {
        return ResponseEntity.status(failure.failure() == ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND ? 404 : 503)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ArtifactApplicationController.ArtifactAccessError(failure.failure().name(), failure.getMessage()));
    }
}
