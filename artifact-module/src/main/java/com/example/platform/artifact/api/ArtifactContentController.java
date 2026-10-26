package com.example.platform.artifact.api;

import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Authentication and project authorization precede logical lookup, storage access and response headers. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/render-jobs/{renderJobId}/artifacts")
public class ArtifactContentController {
    private final ArtifactContentRead contents;
    private final ArtifactProjectAuthorizationPort authorization;
    public ArtifactContentController(ArtifactContentRead contents,ArtifactProjectAuthorizationPort authorization){this.contents=contents;this.authorization=authorization;}
    @GetMapping("/{artifactId}/content")
    public ResponseEntity<?> content(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String renderJobId,@PathVariable String artifactId) {
        authorization.requireRead(tenantId,projectId);
        try {
            var content=contents.read(new ArtifactScope(tenantId,projectId,renderJobId),new ArtifactId(artifactId));
            byte[] bytes=content.bytes();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).contentLength(bytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(content.fileName()).build().toString())
                    .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
                    .eTag('"'+content.digest().canonicalValue()+'"').body(bytes);
        } catch(ArtifactApplicationService.ArtifactAccessException e) {
            return ResponseEntity.status(e.failure()==ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND?404:503)
                    .header(HttpHeaders.CACHE_CONTROL,"no-store").body(new ArtifactApplicationController.ArtifactAccessError(e.failure().name(),e.getMessage()));
        }
    }
}
