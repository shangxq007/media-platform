package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.render.app.cache.RenderCacheCleanupService;
import com.example.platform.render.app.timeline.TimelineAssetGcService;
import com.example.platform.security.AdminAuditHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asset-governance")
@Tag(name = "Asset Governance", description = "Cross-layer GC and global integrity scans")
public class AssetGovernanceController {

    private final GlobalAssetIntegrityService globalIntegrityService;
    private final TimelineAssetGcService timelineAssetGcService;
    private final ArtifactGcService artifactGcService;
    private final StorageBucketOrphanScanner bucketOrphanScanner;
    private final StorageOrphanPurgeService orphanPurgeService;
    private final RenderCacheCleanupService renderCacheCleanupService;
    private final AdminAuditHelper auditHelper;

    public AssetGovernanceController(
            GlobalAssetIntegrityService globalIntegrityService,
            TimelineAssetGcService timelineAssetGcService,
            ArtifactGcService artifactGcService,
            StorageBucketOrphanScanner bucketOrphanScanner,
            StorageOrphanPurgeService orphanPurgeService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    RenderCacheCleanupService renderCacheCleanupService,
            AdminAuditHelper auditHelper) {
        this.globalIntegrityService = globalIntegrityService;
        this.timelineAssetGcService = timelineAssetGcService;
        this.artifactGcService = artifactGcService;
        this.bucketOrphanScanner = bucketOrphanScanner;
        this.orphanPurgeService = orphanPurgeService;
        this.renderCacheCleanupService = renderCacheCleanupService;
        this.auditHelper = auditHelper;
    }

    @PostMapping("/integrity/scan-global")
    @Operation(summary = "跨项目全局完整性扫描", description = "聚合 AST 规则计数并更新 asset.integrity.* 指标")
    public GlobalAssetIntegrityService.GlobalScanReport scanGlobal(
            @RequestParam(defaultValue = "true") boolean recordProblematicData) {
        return globalIntegrityService.scanAll(recordProblematicData);
    }

    @PostMapping("/segment-cache/cleanup")
    @Operation(
            summary = "段缓存 GC（与制品 catalog 分轨）",
            description = "清理已完成 render_job 的 segmentCacheIndex 远程对象；需 render.cache.cleanup-enabled=true")
    public RenderCacheCleanupService.CleanupResult runSegmentCacheCleanup(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        // Platform admin operation: cross-tenant cache cleanup requires ADMIN role
        if (!isAdmin(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            auditHelper.log(request, "ADMIN_SEGMENT_CACHE_CLEANUP", "render_cache", null, tenantId, "DENIED");
            throw new IllegalStateException("Admin role required for segment cache cleanup");
        }
        if (renderCacheCleanupService == null) {
            throw new IllegalStateException("RenderCacheCleanupService is not available");
        }
        RenderCacheCleanupService.CleanupResult result = renderCacheCleanupService.runCleanup(tenantId, projectId);
        auditHelper.log(request, "ADMIN_SEGMENT_CACHE_CLEANUP", "render_cache", null, tenantId, "SUCCESS");
        return result;
    }

    @PostMapping("/storage-orphans/purge")
    @Operation(
            summary = "受控清理桶级孤儿对象",
            description = "需配置 platform.storage.orphan-purge.approval-token；默认 dryRun=true")
    public StorageOrphanPurgeService.PurgeResult purgeStorageOrphans(
            @RequestParam @NotBlank String approvalToken,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(required = false) List<String> storageUri) {
        return orphanPurgeService.purge(dryRun, approvalToken, storageUri);
    }

    private static boolean isAdmin(jakarta.servlet.http.HttpServletRequest request) {
        if (request.isUserInRole("ADMIN")) return true;
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && "ADMIN".equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if ("ADMIN".equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }

    public record CombinedGcResult(
            ArtifactGcService.GcResult artifactGc,
            TimelineAssetGcService.GcRunResult timelineGc) {}
}
