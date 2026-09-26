package com.example.platform.artifact.api;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.shared.web.TenantContext;
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
    private final ArtifactProjectAuthorizationPort projectAuthorization;

    public ArtifactLifecycleController(ArtifactLifecycleService lifecycleService,
                                       ArtifactGcService gcService,
                                       ArtifactProjectAuthorizationPort projectAuthorization) {
        this.lifecycleService = lifecycleService;
        this.gcService = gcService;
        this.projectAuthorization = projectAuthorization;
    }

    @GetMapping("/{artifactId}/delete-check")
    public ArtifactLifecycleService.DeleteCheckResult deleteCheck(
            @PathVariable String artifactId,
            @RequestParam String projectId) {
        String tenantId = requireCurrentTenant();
        projectAuthorization.requireRead(tenantId, projectId);
        return lifecycleService.deleteCheck(tenantId, artifactId);
    }

    @PostMapping("/{artifactId}/tombstone")
    public TombstoneResponse tombstone(
            @PathVariable String artifactId,
            @RequestParam String projectId) {
        String tenantId = requireCurrentTenant();
        projectAuthorization.requireWrite(tenantId, projectId);
        var result = lifecycleService.tombstone(tenantId, artifactId);
        return new TombstoneResponse(
                result.id(), result.projectId(), result.status().name(), result.tombstonedAt());
    }

    @PostMapping("/gc/run")
    public ArtifactGcService.GcResult runGc(
            @RequestParam String projectId,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            @RequestParam(value = "retentionDays", defaultValue = "7") int retentionDays,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        String tenantId = requireCurrentTenant();
        projectAuthorization.requireWrite(tenantId, projectId);
        return gcService.runGc(tenantId, projectId, retentionDays, dryRun, limit);
    }

    private static String requireCurrentTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            throw new IllegalStateException("Tenant context is required for Artifact lifecycle operations");
        }
        return tenantId;
    }

    /** Redacted lifecycle response; storage coordinates remain internal. */
    public record TombstoneResponse(
            String artifactId, String projectId, String state, java.time.Instant tombstonedAt) {}
}
