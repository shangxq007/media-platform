package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.EntitlementBundleService;
import com.example.platform.entitlement.domain.EntitlementBundle;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/entitlements/bundles")
public class EntitlementBundleController {

    private final EntitlementBundleService bundleService;

    public EntitlementBundleController(EntitlementBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @PostMapping
    public EntitlementBundle createBundle(
            @RequestBody CreateBundleRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return bundleService.createBundle(
                request.bundleKey(), request.name(), request.description(),
                request.gpuAllowed(), request.remoteWorkerAllowed(), request.customFontsAllowed(),
                request.maxSubtitleTracks(), request.maxConcurrentJobs(),
                request.monthlyRenderMinutes(), request.storageLimitBytes(),
                request.watermarkRequired(), request.priorityQueueAllowed(),
                request.betaEffectsAllowed(), request.promptExecutionLimit(),
                request.extensionExecutionAllowed(), request.apiAccessAllowed(),
                request.mcpAccessAllowed(), effectiveActor);
    }

    @GetMapping
    public Map<String, Object> listBundles() {
        return Map.of("bundles", bundleService.listBundles());
    }

    @GetMapping("/{bundleKey}")
    public EntitlementBundle getBundle(@PathVariable String bundleKey) {
        return bundleService.getBundle(bundleKey)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleKey));
    }

    @PutMapping("/{bundleKey}")
    public EntitlementBundle updateBundle(
            @PathVariable String bundleKey,
            @RequestBody UpdateBundleRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return bundleService.updateBundle(
                bundleKey, request.name(), request.description(),
                request.gpuAllowed(), request.remoteWorkerAllowed(), request.customFontsAllowed(),
                request.maxSubtitleTracks(), request.maxConcurrentJobs(),
                request.monthlyRenderMinutes(), request.storageLimitBytes(),
                request.watermarkRequired(), request.priorityQueueAllowed(),
                request.betaEffectsAllowed(), request.promptExecutionLimit(),
                request.extensionExecutionAllowed(), request.apiAccessAllowed(),
                request.mcpAccessAllowed(), effectiveActor);
    }

    @PostMapping("/{bundleKey}/archive")
    public EntitlementBundle archiveBundle(
            @PathVariable String bundleKey,
            @RequestHeader(value = "X-User-ID", required = false) String actor) {
        String effectiveActor = actor != null ? actor : "system";
        return bundleService.archiveBundle(bundleKey, effectiveActor);
    }

    public record CreateBundleRequest(
            String bundleKey, String name, String description,
            boolean gpuAllowed, boolean remoteWorkerAllowed, boolean customFontsAllowed,
            int maxSubtitleTracks, int maxConcurrentJobs,
            long monthlyRenderMinutes, long storageLimitBytes,
            boolean watermarkRequired, boolean priorityQueueAllowed,
            boolean betaEffectsAllowed, long promptExecutionLimit,
            boolean extensionExecutionAllowed, boolean apiAccessAllowed, boolean mcpAccessAllowed) {}

    public record UpdateBundleRequest(
            String name, String description,
            boolean gpuAllowed, boolean remoteWorkerAllowed, boolean customFontsAllowed,
            int maxSubtitleTracks, int maxConcurrentJobs,
            long monthlyRenderMinutes, long storageLimitBytes,
            boolean watermarkRequired, boolean priorityQueueAllowed,
            boolean betaEffectsAllowed, long promptExecutionLimit,
            boolean extensionExecutionAllowed, boolean apiAccessAllowed, boolean mcpAccessAllowed) {}
}
