package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.render.app.cache.RenderCacheCleanupService;
import com.example.platform.render.app.timeline.TimelineAssetGcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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

    public AssetGovernanceController(
            GlobalAssetIntegrityService globalIntegrityService,
            TimelineAssetGcService timelineAssetGcService,
            ArtifactGcService artifactGcService,
            StorageBucketOrphanScanner bucketOrphanScanner,
            StorageOrphanPurgeService orphanPurgeService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    RenderCacheCleanupService renderCacheCleanupService) {
        this.globalIntegrityService = globalIntegrityService;
        this.timelineAssetGcService = timelineAssetGcService;
        this.artifactGcService = artifactGcService;
        this.bucketOrphanScanner = bucketOrphanScanner;
        this.orphanPurgeService = orphanPurgeService;
        this.renderCacheCleanupService = renderCacheCleanupService;
    }

    @PostMapping("/integrity/scan-global")
    @Operation(summary = "跨项目全局完整性扫描", description = "聚合 AST 规则计数并更新 asset.integrity.* 指标")
    public GlobalAssetIntegrityService.GlobalScanReport scanGlobal(
            @RequestParam(defaultValue = "true") boolean recordProblematicData) {
        return globalIntegrityService.scanAll(recordProblematicData);
    }

    @PostMapping("/gc/run-all")
    @Operation(summary = "运行制品与时间线 GC", description = "先制品 catalog GC，再时间线 assetRegistry GC")
    public CombinedGcResult runAllGc() {
        ArtifactGcService.GcResult artifact = artifactGcService.runGc();
        TimelineAssetGcService.GcRunResult timeline = timelineAssetGcService.runGlobalGc();
        return new CombinedGcResult(artifact, timeline);
    }

    @PostMapping("/timeline-gc/run")
    @Operation(summary = "时间线 assetRegistry GC")
    public TimelineAssetGcService.GcRunResult runTimelineGc() {
        return timelineAssetGcService.runGlobalGc();
    }

    @PostMapping("/timeline-gc/run-project")
    public TimelineAssetGcService.GcProjectResult runTimelineGcForProject(
            @RequestParam @NotBlank String projectId) {
        return timelineAssetGcService.runProjectGc(projectId, null);
    }

    @PostMapping("/storage-orphans/scan")
    @Operation(summary = "桶级孤儿对象扫描", description = "列举配置桶中的对象并与已知 URI 索引比对（AST-005）")
    public StorageBucketOrphanScanner.OrphanScanResult scanStorageOrphans() {
        return bucketOrphanScanner.scanBuckets();
    }

    @PostMapping("/segment-cache/cleanup")
    @Operation(
            summary = "段缓存 GC（与制品 catalog 分轨）",
            description = "清理已完成 render_job 的 segmentCacheIndex 远程对象；需 render.cache.cleanup-enabled=true")
    public RenderCacheCleanupService.CleanupResult runSegmentCacheCleanup(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId) {
        if (renderCacheCleanupService == null) {
            throw new IllegalStateException("RenderCacheCleanupService is not available");
        }
        return renderCacheCleanupService.runCleanup(tenantId, projectId);
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

    public record CombinedGcResult(
            ArtifactGcService.GcResult artifactGc,
            TimelineAssetGcService.GcRunResult timelineGc) {}
}
