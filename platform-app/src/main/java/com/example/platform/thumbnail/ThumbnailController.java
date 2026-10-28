package com.example.platform.thumbnail;

import com.example.platform.config.mediaexecution.ArtifactCapabilityApplicationService;
import com.example.platform.contract.media.ThumbnailContracts;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort.CapabilityHandle;
import com.example.platform.execution.admission.PlatformExecutionState;
import com.example.platform.artifact.app.*;
import com.example.platform.shared.identity.ArtifactId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Thin compatibility-free capability HTTP adapter over formal Execution and Artifact owner ports. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/thumbnails")
public class ThumbnailController {
    private final ArtifactCapabilityApplicationService executions;
    private final ArtifactContentRead contents;
    public ThumbnailController(ArtifactCapabilityApplicationService executions,ArtifactContentRead contents) {
        this.executions=executions;this.contents=contents;
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CapabilityHandle submit(@PathVariable String tenantId,@PathVariable String projectId,@RequestBody Submit body) {
        var request=new ThumbnailContracts.Request(tenantId,projectId,body.sourceAssetId(),body.timestampSeconds(),body.imageFormat(),
                body.width(),body.quality(),body.idempotencyKey());
        return executions.submit(tenantId,projectId,ThumbnailContracts.CAPABILITY,request.sourceAssetId(),
                request.capabilityParameters(),request.idempotencyKey()).handle();
    }
    @GetMapping("/{executionId}") public CapabilityHandle status(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String executionId) {
        return executions.find(tenantId,projectId,executionId,ThumbnailContracts.CAPABILITY)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)).handle();
    }
    @PostMapping("/{executionId}/cancel") public CapabilityHandle cancel(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String executionId,@RequestBody Control control) {
        return executions.cancel(tenantId,projectId,executionId,control.generation(),ThumbnailContracts.CAPABILITY).handle();
    }
    @PostMapping("/{executionId}/retry") public CapabilityHandle retry(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String executionId,@RequestBody Control control) {
        return executions.retry(tenantId,projectId,executionId,control.generation(),ThumbnailContracts.CAPABILITY).handle();
    }
    @GetMapping("/{executionId}/image") public ResponseEntity<byte[]> image(@PathVariable String tenantId,@PathVariable String projectId,@PathVariable String executionId) {
        // Execution lookup authorizes the project before selecting its exact committed result.
        var execution=executions.find(tenantId,projectId,executionId,ThumbnailContracts.CAPABILITY)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(execution.state()!=PlatformExecutionState.COMPLETED||execution.artifactId()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"output not committed");
        var image=contents.readProjectArtifact(new ArtifactProjectScope(tenantId,projectId),new ArtifactId(execution.artifactId()));
        var bytes=image.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).contentLength(bytes.length)
                .header("X-Artifact-Id",execution.artifactId()).header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff").eTag('"'+image.digest().canonicalValue()+'"').body(bytes);
    }
    public record Submit(String sourceAssetId,double timestampSeconds,String imageFormat,Integer width,Integer quality,String idempotencyKey) {}
    public record Control(long generation) {}
}
