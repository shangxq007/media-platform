package com.example.platform.web.render;

import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Artifact Content", description = "Artifact content access")
public class ArtifactContentController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactContentController.class);
    private final RenderOrchestratorPort orchestratorPort;

    public ArtifactContentController(@Autowired(required = false) RenderOrchestratorPort orchestratorPort) {
        this.orchestratorPort = orchestratorPort;
    }

    @GetMapping("/render/jobs/{jobId}/artifacts/{artifactId}/content")
    @Operation(summary = "Get artifact content")
    public ResponseEntity<byte[]> getArtifactContent(
            @PathVariable String jobId,
            @PathVariable String artifactId) {
        if (orchestratorPort == null) {
            throw new IllegalStateException("Render orchestrator not available");
        }
        List<ArtifactInfoResponse> artifacts = orchestratorPort.getArtifactsByJob(jobId);
        boolean belongsToJob = artifacts.stream().anyMatch(a -> a.artifactId().equals(artifactId));
        if (!belongsToJob) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = orchestratorPort.getArtifactContent(artifactId);
        if (content == null || content.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", "video/mp4")
                .header("Content-Disposition", "inline; filename=output.mp4")
                .header("Content-Length", String.valueOf(content.length))
                .body(content);
    }
}
