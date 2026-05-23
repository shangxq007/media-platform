package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import com.example.platform.artifact.domain.Artifact;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactLifecycleController {

    private final ArtifactLifecycleService lifecycleService;
    private final ArtifactGcService gcService;

    public ArtifactLifecycleController(ArtifactLifecycleService lifecycleService,
                                       ArtifactGcService gcService) {
        this.lifecycleService = lifecycleService;
        this.gcService = gcService;
    }

    @GetMapping("/{artifactId}/delete-check")
    public ArtifactLifecycleService.DeleteCheckResult deleteCheck(@PathVariable String artifactId) {
        return lifecycleService.deleteCheck(artifactId);
    }

    @PostMapping("/{artifactId}/tombstone")
    public Artifact tombstone(@PathVariable String artifactId) {
        return lifecycleService.tombstone(artifactId);
    }

    @PostMapping("/gc/run")
    public ArtifactGcService.GcResult runGc() {
        return gcService.runGc();
    }
}
