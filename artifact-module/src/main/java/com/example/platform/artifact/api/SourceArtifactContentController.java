package com.example.platform.artifact.api;

import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Read-only source-media bytes for the NLE preview; no Timeline or Artifact write. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/source-artifacts")
public class SourceArtifactContentController {
    private final ArtifactContentRead contents;
    private final ArtifactProjectAuthorizationPort projectAuthorization;
    public SourceArtifactContentController(ArtifactContentRead contents, ArtifactProjectAuthorizationPort projectAuthorization) {
        this.contents = contents; this.projectAuthorization = projectAuthorization;
    }
    @GetMapping("/{artifactId}/content")
    public ResponseEntity<?> content(@PathVariable String tenantId, @PathVariable String projectId, @PathVariable String artifactId) {
        projectAuthorization.requireRead(tenantId, projectId);
        try {
            var content = contents.readProjectSourceArtifact(new ArtifactProjectScope(tenantId, projectId), new ArtifactId(artifactId));
            byte[] bytes = content.bytes();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).contentLength(bytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.fileName()).build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store").header("X-Content-Type-Options", "nosniff").body(bytes);
        } catch (ArtifactApplicationService.ArtifactAccessException e) {
            return ResponseEntity.status(e.failure() == ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND ? 404 : 503)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").body(new ArtifactApplicationController.ArtifactAccessError(e.failure().name(), e.getMessage()));
        }
    }
}
