package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifacts")
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
    public TombstoneResponse tombstone(@PathVariable String artifactId) {
        var result = lifecycleService.tombstone(artifactId);
        return new TombstoneResponse(
                result.id(), result.projectId(), result.status().name(), result.tombstonedAt());
    }

    @PostMapping("/gc/run")
    public ArtifactGcService.GcResult runGc(
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            @RequestParam(value = "retentionDays", defaultValue = "7") int retentionDays,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return gcService.runGc(retentionDays, dryRun, limit);
    }

    /** Redacted lifecycle response; storage coordinates remain internal. */
    public record TombstoneResponse(
            String artifactId, String projectId, String state, java.time.Instant tombstonedAt) {}
}
